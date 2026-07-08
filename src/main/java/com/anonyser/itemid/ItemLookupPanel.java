package com.anonyser.itemid;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

/**
 * The item search side panel: type in the box and matching items populate live; click one to see its
 * icon, name, ID (click the ID to copy it), value info and, for equipment, its full bonus stats.
 * The value block leads with the GE price (or "Untradeable"), then high alch and, for repairable
 * untradeables, the repair cost. Search runs over every item in the game off a name index the plugin
 * builds once. All display-only.
 */
class ItemLookupPanel extends PluginPanel
{
	private static final int MAX_RESULTS = 25;
	private static final int MAX_COMPARE_RESULTS = 8;
	private static final Color BETTER = new Color(0, 200, 83);
	private static final Color GE_VALUE = new Color(255, 214, 120);

	/**
	 * One searchable item: its id, name (lowercased for matching), whether it is tradeable, and
	 * whether it is actually on the Grand Exchange (has a GE buy limit) -- which is what separates the
	 * real item from same-named LMS/beta/tournament copies that are "tradeable" but not on the GE.
	 */
	static final class Item
	{
		final int id;
		final String name;
		final String lower;
		final boolean tradeable;
		// Starts from the static GE buy-limit (built before the price feed loads); corrected to the
		// live "has a GE price" answer the first time the item's value is fetched. volatile: written on
		// the client thread, read on the EDT.
		volatile boolean onGe;

		Item(int id, String name, boolean tradeable, boolean onGe)
		{
			this.id = id;
			this.name = name;
			this.lower = name.toLowerCase();
			this.tradeable = tradeable;
			this.onGe = onGe;
		}
	}

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final RepairCosts repairCosts;
	private final ItemIdConfig config;
	private final WikiDescription wiki;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel resultsPanel = new JPanel();
	private List<Item> itemIndex;
	private Map<Integer, int[]> recipes = Collections.emptyMap();
	// Bumped each search; async value lookups only apply if their generation still matches.
	private int resultsGen;

	// Detail area, hidden until an item is picked: a card (icon + value info) and a bonus-stats grid.
	private final JPanel detail = new JPanel();
	private final JPanel detailCard = new JPanel(new BorderLayout(8, 0));
	private final JLabel detailIcon = new JLabel();
	private final JLabel detailName = new JLabel();
	private final JLabel detailId = new JLabel();
	private final JLabel detailGe = new JLabel();
	private final JLabel detailHa = new JLabel();
	private final JLabel detailRepair = new JLabel();
	private final JLabel detailBase = new JLabel();
	private final JLabel detailWiki = new JLabel("Open wiki page");
	private final JTextArea detailDesc = new JTextArea();
	private final JPanel detailStats = new JPanel(new GridLayout(0, 2, 8, 1));
	private final JPanel madeFrom = new JPanel();
	private int selectedId = -1;
	private String selectedName;
	// The tradeable base an item prices as (via ItemMapping), for the clickable "Prices as" line.
	private int baseItemId = -1;
	private String baseItemName;

	// Compare feature: a button, a second search, and the side-by-side stat grid.
	private final JButton compareButton = new JButton("Compare with another item");
	private final JPanel compareSection = new JPanel();
	private final IconTextField compareBar = new IconTextField();
	private final JPanel compareResults = new JPanel();
	private final JPanel comparePanel = new JPanel(new GridLayout(0, 3, 6, 1));

	ItemLookupPanel(ItemManager itemManager, ClientThread clientThread, RepairCosts repairCosts,
		ItemIdConfig config, WikiDescription wiki)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.repairCosts = repairCosts;
		this.config = config;
		this.wiki = wiki;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PANEL_WIDTH, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh();
			}
		});
		searchBar.addClearListener(this::refresh);

		buildDetail();
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

		final JPanel top = new JPanel(new BorderLayout(0, 8));
		top.add(searchBar, BorderLayout.NORTH);
		top.add(detail, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(resultsPanel, BorderLayout.CENTER);
	}

	/** EDT: hand the panel the finished item index; the current search is then re-run. */
	void setItemIndex(List<Item> index)
	{
		this.itemIndex = index;
		refresh();
	}

	/** EDT: the resolved combination recipes (result item id -> component item ids). */
	void setRecipes(Map<Integer, int[]> recipes)
	{
		this.recipes = recipes != null ? recipes : Collections.emptyMap();
	}

	private void buildDetail()
	{
		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		detail.setVisible(false);

		detailCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		detailCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailCard.setAlignmentX(LEFT_ALIGNMENT);

		detailIcon.setPreferredSize(new Dimension(40, 36));
		detailIcon.setHorizontalAlignment(SwingConstants.CENTER);
		detailIcon.setVerticalAlignment(SwingConstants.TOP);

		final JPanel textCol = new JPanel();
		textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
		textCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailName.setFont(FontManager.getRunescapeBoldFont());
		detailName.setForeground(Color.WHITE);
		detailId.setForeground(ColorScheme.BRAND_ORANGE);
		detailId.setToolTipText("Click to copy the item ID");
		detailGe.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailHa.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailRepair.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailBase.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		detailBase.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		detailBase.setToolTipText("Click to look up the base item");
		detailBase.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (baseItemId > 0 && baseItemName != null)
				{
					select(baseItemId, baseItemName);
				}
			}
		});
		detailWiki.setForeground(new Color(120, 180, 255));
		detailWiki.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		detailWiki.setToolTipText("Open this item's OSRS wiki page in your browser");
		detailWiki.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (selectedName != null)
				{
					LinkBrowser.browse(wikiUrl(selectedName));
				}
			}
		});
		textCol.add(detailName);
		textCol.add(detailId);
		textCol.add(detailGe);
		textCol.add(detailHa);
		textCol.add(detailRepair);
		textCol.add(detailBase);
		textCol.add(detailWiki);

		detailId.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (selectedId > 0)
				{
					Toolkit.getDefaultToolkit().getSystemClipboard()
						.setContents(new StringSelection(Integer.toString(selectedId)), null);
					detailId.setText("ID: " + selectedId + "  (copied)");
				}
			}
		});

		detailCard.add(detailIcon, BorderLayout.WEST);
		detailCard.add(textCol, BorderLayout.CENTER);

		detailDesc.setEditable(false);
		detailDesc.setFocusable(false);
		detailDesc.setLineWrap(true);
		detailDesc.setWrapStyleWord(true);
		detailDesc.setOpaque(false);
		detailDesc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailDesc.setFont(FontManager.getRunescapeFont());
		detailDesc.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
		detailDesc.setAlignmentX(LEFT_ALIGNMENT);
		detailDesc.setVisible(false);

		detailStats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailStats.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		detailStats.setAlignmentX(LEFT_ALIGNMENT);
		detailStats.setVisible(false);

		madeFrom.setLayout(new BoxLayout(madeFrom, BoxLayout.Y_AXIS));
		madeFrom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		madeFrom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		madeFrom.setAlignmentX(LEFT_ALIGNMENT);
		madeFrom.setVisible(false);

		compareButton.setFocusPainted(false);
		compareButton.setAlignmentX(LEFT_ALIGNMENT);
		compareButton.addActionListener(e -> toggleCompare());

		compareBar.setIcon(IconTextField.Icon.SEARCH);
		compareBar.setPreferredSize(new Dimension(PANEL_WIDTH, 28));
		compareBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		compareBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshCompareResults();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshCompareResults();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshCompareResults();
			}
		});
		compareBar.addClearListener(this::refreshCompareResults);

		compareResults.setLayout(new BoxLayout(compareResults, BoxLayout.Y_AXIS));
		compareResults.setAlignmentX(LEFT_ALIGNMENT);

		comparePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		comparePanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		comparePanel.setAlignmentX(LEFT_ALIGNMENT);
		comparePanel.setVisible(false);

		compareSection.setLayout(new BoxLayout(compareSection, BoxLayout.Y_AXIS));
		compareSection.setAlignmentX(LEFT_ALIGNMENT);
		compareSection.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		compareSection.setVisible(false);
		compareSection.add(compareBar);
		compareSection.add(compareResults);
		compareSection.add(comparePanel);

		detail.add(detailCard);
		detail.add(detailDesc);
		detail.add(detailStats);
		detail.add(madeFrom);
		detail.add(compareButton);
		detail.add(compareSection);
	}

	private void refresh()
	{
		resultsPanel.removeAll();
		final int gen = ++resultsGen;
		final String raw = searchBar.getText();
		final String query = raw == null ? "" : raw.trim().toLowerCase();
		if (itemIndex == null)
		{
			resultsPanel.add(mutedRow("Loading item list..."));
		}
		else if (!query.isEmpty())
		{
			final List<Item> found = matches(query);
			if (found.isEmpty())
			{
				resultsPanel.add(mutedRow("No matches"));
			}
			else
			{
				final List<JLabel> values = new ArrayList<>();
				final List<JLabel> names = new ArrayList<>();
				for (Item item : found)
				{
					final JLabel value = valueLabel();
					resultsPanel.add(resultRow(item, value, names));
					values.add(value);
				}
				fetchResultValues(gen, found, values, names);
			}
		}
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	/**
	 * Fills each visible row's right-hand value off the client thread: GE price for items on the GE,
	 * repair cost for repairable ones, otherwise a plain not-on-GE/untradeable note. Only applied if
	 * the search hasn't moved on since (generation guard).
	 */
	private void fetchResultValues(int gen, List<Item> items, List<JLabel> labels, List<JLabel> nameLabels)
	{
		clientThread.invoke(() ->
		{
			final String[] text = new String[items.size()];
			final Color[] color = new Color[items.size()];
			final boolean[] onGe = new boolean[items.size()];
			for (int i = 0; i < items.size(); i++)
			{
				final Item it = items.get(i);
				// "On the GE" really means "has a live GE price". The static buy-limit used to build
				// the index doesn't cover every GE item (e.g. Ultor ring 28307), so confirm it here
				// where the price feed is loaded, keeping the buy-limit answer as a floor (never off).
				final int price = it.tradeable ? itemManager.getItemPrice(it.id) : 0;
				final boolean isOnGe = it.onGe || price > 0;
				it.onGe = isOnGe; // correct the shared flag so the row dim and later renders follow
				onGe[i] = isOnGe;
				if (isOnGe)
				{
					text[i] = price > 0 ? abbreviate(price) : "";
					color[i] = GE_VALUE;
				}
				else
				{
					final Integer repair = repairCosts.cost(it.name);
					text[i] = repair != null ? abbreviate(repair)
						: (it.tradeable ? "not on GE" : "untradeable");
					color[i] = ColorScheme.MEDIUM_GRAY_COLOR;
				}
			}
			SwingUtilities.invokeLater(() ->
			{
				if (gen != resultsGen)
				{
					return;
				}
				for (int i = 0; i < labels.size() && i < text.length; i++)
				{
					labels.get(i).setText(text[i]);
					labels.get(i).setForeground(color[i]);
				}
				for (int i = 0; i < nameLabels.size() && i < onGe.length; i++)
				{
					nameLabels.get(i).setForeground(onGe[i]
						? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
				}
			});
		});
	}

	private static JLabel valueLabel()
	{
		final JLabel label = new JLabel();
		label.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 6));
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	/** Short gp form: 7,301,390 -> "7.3M", 150,000 -> "150K". */
	private static String abbreviate(long v)
	{
		if (v >= 1_000_000_000L)
		{
			return trimDecimal(v / 1e9) + "B";
		}
		if (v >= 1_000_000L)
		{
			return trimDecimal(v / 1e6) + "M";
		}
		if (v >= 1_000L)
		{
			return trimDecimal(v / 1e3) + "K";
		}
		return Long.toString(v);
	}

	private static String trimDecimal(double d)
	{
		final String s = String.format("%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	/** Name-substring match, with names that start with the query first, then shortest names. */
	private List<Item> matches(String query)
	{
		final List<Item> starts = new ArrayList<>();
		final List<Item> contains = new ArrayList<>();
		for (Item item : itemIndex)
		{
			if (item.lower.startsWith(query))
			{
				starts.add(item);
			}
			else if (item.lower.contains(query))
			{
				contains.add(item);
			}
		}
		final Comparator<Item> byName =
			Comparator.<Item>comparingInt(i -> i.name.length()).thenComparing(i -> i.name);
		starts.sort(byName);
		contains.sort(byName);
		final List<Item> out = new ArrayList<>(starts);
		out.addAll(contains);
		return out.size() > MAX_RESULTS ? new ArrayList<>(out.subList(0, MAX_RESULTS)) : out;
	}

	private JPanel resultRow(Item item, JLabel value, List<JLabel> nameLabels)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Items not on the GE are dimmed; the right-hand value (filled in async) is the "which one is
		// the real item" cue: a GE price for the tradeable one, a repair cost or a note otherwise. The
		// dim reads item.onGe live so the async value fetch can un-dim an item whose on-GE status was
		// under-reported by the static buy-limit (see fetchResultValues).
		final JLabel label = new JLabel(item.name);
		label.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		label.setForeground(item.onGe ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		label.setOpaque(false);
		nameLabels.add(label);
		row.add(label, BorderLayout.CENTER);
		row.add(value, BorderLayout.EAST);

		// Listener on the name label only, so enter/exit stay clean single-component events.
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				select(item.id, item.name);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				label.setForeground(item.onGe ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
			}
		});
		return row;
	}

	private static JLabel mutedRow(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		return label;
	}

	/** The right-hand tag for a result: nothing if on the GE, otherwise why it is not. */
	private static String tag(Item item)
	{
		if (item.onGe)
		{
			return "";
		}
		return item.tradeable ? "  (not on GE)" : "  (untradeable)";
	}

	private void select(int id, String name)
	{
		// EDT: only Swing and the local repair map. Everything that reads ItemManager (price,
		// composition, stats, image) asserts the client thread, so it is done in loadDetails.
		selectedId = id;
		selectedName = name;
		detailName.setText(name);
		detailId.setText("ID: " + id);
		detailGe.setText("GE: ...");
		detailHa.setText("High alch: ...");

		final Integer repair = repairCosts.cost(name);
		if (repair != null)
		{
			detailRepair.setText("Repair: " + gp(repair));
			detailRepair.setVisible(true);
		}
		else
		{
			detailRepair.setVisible(false);
		}
		detailBase.setVisible(false);
		detailStats.setVisible(false);
		detailDesc.setVisible(false);
		madeFrom.setVisible(false);
		compareSection.setVisible(false);
		comparePanel.setVisible(false);
		detailIcon.setIcon(null);

		detail.setVisible(true);
		// showing the detail changes the north region's height; reflow the whole panel.
		revalidate();
		repaint();

		clientThread.invoke(() -> loadDetails(id));

		if (config.showWikiDescription())
		{
			wiki.fetch(id, name, (fid, desc) -> SwingUtilities.invokeLater(() -> showDescription(fid, desc)));
		}
	}

	private void showDescription(int id, String desc)
	{
		if (id != selectedId || desc == null || desc.isEmpty())
		{
			return;
		}
		detailDesc.setText(desc);
		detailDesc.setVisible(true);
		revalidate();
		repaint();
	}

	/** Client thread: read the item's price, composition, stats, image and mapping; update on EDT. */
	private void loadDetails(int id)
	{
		final ItemComposition comp = itemManager.getItemComposition(id);
		final boolean tradeable = comp.isTradeable();
		final int ge = itemManager.getItemPrice(id);
		final int ha = comp.getHaPrice();
		final AsyncBufferedImage img = itemManager.getImage(id);
		final ItemStats stats = itemManager.getItemStats(id);
		final ItemEquipmentStats eq = stats != null ? stats.getEquipment() : null;

		// Any item that maps to a different tradeable base (ornament, BH-corrupted, degraded, or a
		// Deadman/beta variant that shares a name) prices as that base and is flagged as such.
		// ItemMapping.map returns null (not an empty collection) when there is no mapping.
		String baseName = null;
		int baseId = -1;
		final Collection<ItemMapping> mappings = ItemMapping.map(id);
		if (mappings != null)
		{
			for (ItemMapping m : mappings)
			{
				final int b = m.getTradeableItem();
				if (b > 0 && b != id)
				{
					final ItemComposition baseComp = itemManager.getItemComposition(b);
					if (baseComp != null)
					{
						baseName = baseComp.getName();
						baseId = b;
						break;
					}
				}
			}
		}
		final String base = baseName;
		final int mappedBaseId = baseId;

		// Combination components (if this item has a recipe): each part's name and GE price.
		final int[] recipe = recipes.get(id);
		List<Comp> comps = null;
		long total = 0;
		if (recipe != null)
		{
			comps = new ArrayList<>(recipe.length);
			for (int cid : recipe)
			{
				final ItemComposition cc = itemManager.getItemComposition(cid);
				final int cp = itemManager.getItemPrice(cid);
				comps.add(new Comp(cid, cc != null ? cc.getName() : "?", cp));
				total += cp;
			}
		}
		final List<Comp> components = comps;
		final long componentsTotal = total;

		SwingUtilities.invokeLater(() ->
		{
			if (id != selectedId)
			{
				return;
			}
			detailGe.setText(tradeable ? "GE: " + (ge > 0 ? gp(ge) : "not on GE") : "Untradeable");
			detailHa.setText("High alch: " + (ha > 0 ? gp(ha) : "-"));
			img.addTo(detailIcon);
			if (base != null)
			{
				baseItemId = mappedBaseId;
				baseItemName = base;
				detailBase.setText("Prices as: " + base);
				detailBase.setVisible(true);
			}
			else
			{
				baseItemId = -1;
			}
			if (eq != null)
			{
				renderStats(eq);
			}
			if (components != null)
			{
				renderComponents(components, componentsTotal);
			}
			revalidate();
			repaint();
		});
	}

	/** Fills the bonus grid, wiki-style: attack column, defence column, then the other bonuses. */
	private void renderStats(ItemEquipmentStats eq)
	{
		detailStats.removeAll();
		addStatRow(header("Attack"), header("Defence"));
		addStatRow(stat("Stab " + sign(eq.getAstab())), stat("Stab " + sign(eq.getDstab())));
		addStatRow(stat("Slash " + sign(eq.getAslash())), stat("Slash " + sign(eq.getDslash())));
		addStatRow(stat("Crush " + sign(eq.getAcrush())), stat("Crush " + sign(eq.getDcrush())));
		addStatRow(stat("Magic " + sign(eq.getAmagic())), stat("Magic " + sign(eq.getDmagic())));
		addStatRow(stat("Range " + sign(eq.getArange())), stat("Range " + sign(eq.getDrange())));
		addStatRow(stat("Str " + sign(eq.getStr())), stat("Prayer " + sign(eq.getPrayer())));
		addStatRow(stat("Rng str " + sign(eq.getRstr())), stat("Mag dmg " + signPct(eq.getMdmg())));
		addStatRow(stat("Speed " + eq.getAspeed()), stat(""));
		detailStats.setVisible(true);
	}

	private void addStatRow(JLabel left, JLabel right)
	{
		detailStats.add(left);
		detailStats.add(right);
	}

	private static JLabel header(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	private static JLabel stat(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static String sign(int value)
	{
		return String.format("%+d", value);
	}

	private static String signPct(float value)
	{
		return String.format("%+.0f%%", value);
	}

	private static final class Comp
	{
		final int id;
		final String name;
		final int price;

		Comp(int id, String name, int price)
		{
			this.id = id;
			this.name = name;
			this.price = price;
		}
	}

	/** Fills the "Made from" section with each component (clickable) and the total cost to make. */
	private void renderComponents(List<Comp> comps, long total)
	{
		madeFrom.removeAll();
		final JLabel head = header("Made from");
		head.setAlignmentX(LEFT_ALIGNMENT);
		madeFrom.add(head);
		for (Comp c : comps)
		{
			madeFrom.add(componentRow(c));
		}
		final JLabel totalLabel = new JLabel("Total: " + String.format("%,d gp", total));
		totalLabel.setForeground(Color.WHITE);
		totalLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		totalLabel.setAlignmentX(LEFT_ALIGNMENT);
		madeFrom.add(totalLabel);
		madeFrom.setVisible(true);
	}

	private JPanel componentRow(Comp c)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

		final JLabel label = new JLabel(c.name);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		label.setToolTipText("Click to look up " + c.name);
		final JLabel price = new JLabel(c.price > 0 ? gp(c.price) : "-");
		price.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);
		row.add(price, BorderLayout.EAST);

		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				select(c.id, c.name);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(ColorScheme.BRAND_ORANGE);
			}
		});
		return row;
	}

	private void toggleCompare()
	{
		final boolean show = !compareSection.isVisible();
		compareSection.setVisible(show);
		if (show)
		{
			compareBar.setText("");
			compareResults.removeAll();
			comparePanel.setVisible(false);
			compareBar.requestFocusInWindow();
		}
		revalidate();
		repaint();
	}

	private void refreshCompareResults()
	{
		compareResults.removeAll();
		final String raw = compareBar.getText();
		final String query = raw == null ? "" : raw.trim().toLowerCase();
		if (itemIndex != null && !query.isEmpty())
		{
			int shown = 0;
			for (Item item : matches(query))
			{
				if (shown++ >= MAX_COMPARE_RESULTS)
				{
					break;
				}
				compareResults.add(compareResultRow(item));
			}
		}
		compareResults.revalidate();
		compareResults.repaint();
	}

	private JPanel compareResultRow(Item item)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		final String text = item.name + tag(item);
		final Color base = item.onGe ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR;
		final JLabel label = new JLabel(text);
		label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		label.setForeground(base);
		row.add(label, BorderLayout.CENTER);

		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onComparePick(item.id, item.name);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				label.setForeground(base);
			}
		});
		return row;
	}

	private void onComparePick(int bId, String bName)
	{
		final int aId = selectedId;
		final String aName = selectedName;
		if (aId <= 0)
		{
			return;
		}
		compareResults.removeAll();
		compareResults.revalidate();
		compareResults.repaint();
		clientThread.invoke(() ->
		{
			final Snapshot a = snapshot(aId, aName);
			final Snapshot b = snapshot(bId, bName);
			SwingUtilities.invokeLater(() -> renderCompare(a, b));
		});
	}

	private Snapshot snapshot(int id, String name)
	{
		final ItemComposition c = itemManager.getItemComposition(id);
		final int ge = itemManager.getItemPrice(id);
		final int ha = c != null ? c.getHaPrice() : 0;
		final ItemStats s = itemManager.getItemStats(id);
		return new Snapshot(name, ge, ha, s != null ? s.getEquipment() : null);
	}

	private void renderCompare(Snapshot a, Snapshot b)
	{
		comparePanel.removeAll();
		comparePanel.add(cell("", ColorScheme.LIGHT_GRAY_COLOR, false));
		comparePanel.add(headerCell(shortName(a.name)));
		comparePanel.add(headerCell(shortName(b.name)));
		addValueRow("GE", a.ge, b.ge);
		addValueRow("Alch", a.ha, b.ha);
		if (a.eq != null && b.eq != null)
		{
			addCompareRow("Stab atk", a.eq.getAstab(), b.eq.getAstab(), true);
			addCompareRow("Slash atk", a.eq.getAslash(), b.eq.getAslash(), true);
			addCompareRow("Crush atk", a.eq.getAcrush(), b.eq.getAcrush(), true);
			addCompareRow("Magic atk", a.eq.getAmagic(), b.eq.getAmagic(), true);
			addCompareRow("Range atk", a.eq.getArange(), b.eq.getArange(), true);
			addCompareRow("Stab def", a.eq.getDstab(), b.eq.getDstab(), true);
			addCompareRow("Slash def", a.eq.getDslash(), b.eq.getDslash(), true);
			addCompareRow("Crush def", a.eq.getDcrush(), b.eq.getDcrush(), true);
			addCompareRow("Magic def", a.eq.getDmagic(), b.eq.getDmagic(), true);
			addCompareRow("Range def", a.eq.getDrange(), b.eq.getDrange(), true);
			addCompareRow("Str", a.eq.getStr(), b.eq.getStr(), true);
			addCompareRow("Ranged str", a.eq.getRstr(), b.eq.getRstr(), true);
			addCompareRow("Prayer", a.eq.getPrayer(), b.eq.getPrayer(), true);
			addMdmgRow(a.eq.getMdmg(), b.eq.getMdmg());
			addCompareRow("Speed", a.eq.getAspeed(), b.eq.getAspeed(), false);
		}
		comparePanel.setVisible(true);
		revalidate();
		repaint();
	}

	private void addValueRow(String label, int aVal, int bVal)
	{
		comparePanel.add(cell(label, ColorScheme.LIGHT_GRAY_COLOR, false));
		comparePanel.add(cell(aVal > 0 ? gp(aVal) : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
		comparePanel.add(cell(bVal > 0 ? gp(bVal) : "-", ColorScheme.LIGHT_GRAY_COLOR, false));
	}

	private void addCompareRow(String label, int aVal, int bVal, boolean higherBetter)
	{
		final boolean aBetter = higherBetter ? aVal > bVal : aVal < bVal;
		final boolean bBetter = higherBetter ? bVal > aVal : bVal < aVal;
		comparePanel.add(cell(label, ColorScheme.LIGHT_GRAY_COLOR, false));
		comparePanel.add(cell(sign(aVal), aBetter ? BETTER : ColorScheme.LIGHT_GRAY_COLOR, aBetter));
		comparePanel.add(cell(sign(bVal), bBetter ? BETTER : ColorScheme.LIGHT_GRAY_COLOR, bBetter));
	}

	private void addMdmgRow(float aVal, float bVal)
	{
		final boolean aBetter = aVal > bVal;
		final boolean bBetter = bVal > aVal;
		comparePanel.add(cell("Magic dmg", ColorScheme.LIGHT_GRAY_COLOR, false));
		comparePanel.add(cell(signPct(aVal), aBetter ? BETTER : ColorScheme.LIGHT_GRAY_COLOR, aBetter));
		comparePanel.add(cell(signPct(bVal), bBetter ? BETTER : ColorScheme.LIGHT_GRAY_COLOR, bBetter));
	}

	private static JLabel cell(String text, Color color, boolean bold)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		return label;
	}

	private static JLabel headerCell(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}

	private static String shortName(String name)
	{
		return name != null && name.length() > 12 ? name.substring(0, 11) + "…" : name;
	}

	private static final class Snapshot
	{
		final String name;
		final int ge;
		final int ha;
		final ItemEquipmentStats eq;

		Snapshot(String name, int ge, int ha, ItemEquipmentStats eq)
		{
			this.name = name;
			this.ge = ge;
			this.ha = ha;
			this.eq = eq;
		}
	}

	private static String gp(int value)
	{
		return String.format("%,d gp", value);
	}

	/** The OSRS wiki page URL for an item name (the wiki uses underscores for spaces). */
	private static String wikiUrl(String name)
	{
		return "https://oldschool.runescape.wiki/w/" + name.replace(' ', '_');
	}
}
