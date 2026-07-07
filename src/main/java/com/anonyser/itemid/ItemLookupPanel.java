package com.anonyser.itemid;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

/**
 * The item search side panel: type in the box and matching items populate live; click one to see its
 * icon, name, ID (click the ID to copy it), Grand Exchange price, high-alch value and, for repairable
 * untradeables, the repair cost. Ornament / Bounty Hunter corrupted / degraded items price as their
 * tradeable base and say so. Search covers the tradeable item list, so the hover ID is the fallback
 * for anything that isn't tradeable. All display-only.
 */
class ItemLookupPanel extends PluginPanel
{
	private static final int MAX_RESULTS = 25;

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final RepairCosts repairCosts;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel resultsPanel = new JPanel();

	// Detail card, hidden until an item is picked.
	private final JPanel detailCard = new JPanel(new BorderLayout(8, 0));
	private final JLabel detailIcon = new JLabel();
	private final JLabel detailName = new JLabel();
	private final JLabel detailId = new JLabel();
	private final JLabel detailGe = new JLabel();
	private final JLabel detailHa = new JLabel();
	private final JLabel detailRepair = new JLabel();
	private final JLabel detailBase = new JLabel();
	private int selectedId = -1;

	ItemLookupPanel(ItemManager itemManager, ClientThread clientThread, RepairCosts repairCosts)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.repairCosts = repairCosts;
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

		buildDetailCard();
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

		final JPanel top = new JPanel(new BorderLayout(0, 8));
		top.add(searchBar, BorderLayout.NORTH);
		top.add(detailCard, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(resultsPanel, BorderLayout.CENTER);
	}

	private void buildDetailCard()
	{
		detailCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		detailCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailCard.setVisible(false);

		detailIcon.setPreferredSize(new Dimension(40, 36));
		detailIcon.setHorizontalAlignment(SwingConstants.CENTER);
		detailIcon.setVerticalAlignment(SwingConstants.TOP);

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailName.setFont(FontManager.getRunescapeBoldFont());
		detailName.setForeground(Color.WHITE);
		detailId.setForeground(ColorScheme.BRAND_ORANGE);
		detailId.setToolTipText("Click to copy the item ID");
		detailGe.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailHa.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailRepair.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		detailBase.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		text.add(detailName);
		text.add(detailId);
		text.add(detailGe);
		text.add(detailHa);
		text.add(detailRepair);
		text.add(detailBase);

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
		detailCard.add(text, BorderLayout.CENTER);
	}

	private void refresh()
	{
		resultsPanel.removeAll();
		final String query = searchBar.getText() == null ? "" : searchBar.getText().trim();
		if (!query.isEmpty())
		{
			final List<ItemPrice> matches = itemManager.search(query);
			if (matches.isEmpty())
			{
				resultsPanel.add(mutedRow("No matches"));
			}
			else
			{
				int shown = 0;
				for (ItemPrice item : matches)
				{
					if (shown++ >= MAX_RESULTS)
					{
						break;
					}
					resultsPanel.add(resultRow(item.getId(), item.getName()));
				}
			}
		}
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel resultRow(int id, String name)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel label = new JLabel(name);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				select(id, name);
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
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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

	private void select(int id, String name)
	{
		selectedId = id;
		detailName.setText(name);
		detailId.setText("ID: " + id);
		detailGe.setText("GE: " + (itemManager.getItemPrice(id) > 0
			? gp(itemManager.getItemPrice(id)) : "not on GE"));

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

		detailHa.setText("High alch: ...");
		detailBase.setVisible(false);

		detailIcon.setIcon(null);
		final AsyncBufferedImage img = itemManager.getImage(id);
		img.addTo(detailIcon);

		detailCard.setVisible(true);
		detailCard.revalidate();
		detailCard.repaint();

		// getItemComposition / getHaPrice assert the client thread, so read them there and post back.
		clientThread.invoke(() -> loadClientThreadDetails(id));
	}

	private void loadClientThreadDetails(int id)
	{
		final ItemComposition comp = itemManager.getItemComposition(id);
		final int ha = comp.getHaPrice();
		String baseName = null;
		if (!comp.isTradeable())
		{
			for (ItemMapping m : ItemMapping.map(id))
			{
				final int baseId = m.getTradeableItem();
				if (baseId > 0 && baseId != id)
				{
					baseName = itemManager.getItemComposition(baseId).getName();
					break;
				}
			}
		}
		final String base = baseName;
		SwingUtilities.invokeLater(() ->
		{
			if (id != selectedId)
			{
				return;
			}
			detailHa.setText("High alch: " + (ha > 0 ? gp(ha) : "-"));
			if (base != null)
			{
				detailBase.setText("Prices as: " + base);
				detailBase.setVisible(true);
			}
		});
	}

	private static String gp(int value)
	{
		return String.format("%,d gp", value);
	}
}
