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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

/**
 * The item search side panel: type in the box and the matching items populate live; click one to see
 * its icon, name, ID (click the ID to copy it) and current Grand Exchange price. Search covers the
 * tradeable item list the client already has, so untradeable items may not appear here (the hover
 * ID works on any item). All display-only.
 */
class ItemLookupPanel extends PluginPanel
{
	private static final int MAX_RESULTS = 25;

	private final ItemManager itemManager;
	private final IconTextField searchBar = new IconTextField();
	private final JPanel resultsPanel = new JPanel();

	// Detail card, hidden until an item is picked.
	private final JPanel detailCard = new JPanel(new BorderLayout(8, 0));
	private final JLabel detailIcon = new JLabel();
	private final JLabel detailName = new JLabel();
	private final JLabel detailId = new JLabel();
	private final JLabel detailGe = new JLabel();
	private int selectedId = -1;

	ItemLookupPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
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

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailName.setFont(FontManager.getRunescapeBoldFont());
		detailName.setForeground(Color.WHITE);
		detailId.setForeground(ColorScheme.BRAND_ORANGE);
		detailId.setToolTipText("Click to copy the item ID");
		detailGe.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(detailName);
		text.add(detailId);
		text.add(detailGe);

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
		final int price = itemManager.getItemPrice(id);
		detailGe.setText("GE: " + (price > 0 ? String.format("%,d gp", price) : "not on GE"));
		detailIcon.setIcon(null);
		final AsyncBufferedImage img = itemManager.getImage(id);
		img.addTo(detailIcon);
		detailCard.setVisible(true);
		detailCard.revalidate();
		detailCard.repaint();
	}
}
