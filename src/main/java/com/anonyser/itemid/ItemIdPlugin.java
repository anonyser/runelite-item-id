package com.anonyser.itemid;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Item ID and Lookup",
	description = "Show an item's ID on hover, and search items for their ID and GE price",
	tags = {"item", "id", "ids", "lookup", "search", "price", "ge", "wiki"}
)
public class ItemIdPlugin extends Plugin
{
	// White so the appended id stands out from the item's own colour in the hover text.
	private static final String ID_COLOR = "<col=ffffff>";

	@Inject
	private ItemIdConfig config;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ChatMessageManager chatMessageManager;

	private ItemLookupPanel panel;
	private NavigationButton navButton;

	// One-time in-game note after an update ships — sent a few ticks after login, then never again for
	// this version. Keep in step with the build.gradle version.
	private static final String PLUGIN_VERSION = "1.0.1";
	private static final String K_ANNOUNCED = "announcedVersion";
	private boolean pendingUpdateNote;
	private int ticksSinceLogin;

	@Provides
	ItemIdConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemIdConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new ItemLookupPanel(itemManager, clientThread, RepairCosts.load(), config,
			new WikiDescription(okHttpClient, gson));
		navButton = NavigationButton.builder()
			.tooltip("Item lookup")
			.icon(navIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Build the searchable item index off the client thread, resolve the combination recipes to
		// ids from it, then hand both to the panel.
		final ItemLookupPanel p = panel;
		final Recipes recipeData = Recipes.load();
		clientThread.invoke(() ->
		{
			final List<ItemLookupPanel.Item> index = buildItemIndex();
			final Map<String, Integer> nameToId = new HashMap<>();
			for (ItemLookupPanel.Item it : index)
			{
				nameToId.putIfAbsent(it.lower, it.id);
			}
			final Map<Integer, int[]> recipes = recipeData.resolve(nameToId);
			SwingUtilities.invokeLater(() ->
			{
				p.setRecipes(recipes);
				p.setItemIndex(index);
			});
		});
	}

	/** Every named, non-noted, non-placeholder item, so search finds untradeables too. */
	private List<ItemLookupPanel.Item> buildItemIndex()
	{
		final int count = client.getItemCount();
		final List<ItemLookupPanel.Item> list = new ArrayList<>();
		for (int id = 0; id < count; id++)
		{
			final ItemComposition c = itemManager.getItemComposition(id);
			final String name = c.getName();
			if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
			{
				continue;
			}
			if (c.getNote() != -1 || c.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			// A GE buy limit (static stats data, unlike the async price feed) means the item is
			// actually on the GE -- this is what tells the real item from LMS/beta look-alikes.
			final ItemStats st = itemManager.getItemStats(id);
			final boolean onGe = st != null && st.getGeLimit() > 0;
			list.add(new ItemLookupPanel.Item(id, name, c.isTradeable(), onGe));
		}
		return list;
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			ticksSinceLogin = 0;
			pendingUpdateNote = !PLUGIN_VERSION.equals(
				configManager.getConfiguration(ItemIdConfig.GROUP, K_ANNOUNCED));
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		// A few ticks in, so the note isn't lost in the login message burst.
		if (pendingUpdateNote && ++ticksSinceLogin >= 4)
		{
			pendingUpdateNote = false;
			announceUpdate();
		}
	}

	/** One in-game chat note per shipped version; a global config key stops it repeating. */
	private void announceUpdate()
	{
		final String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Item ID and Lookup " + PLUGIN_VERSION + ": ")
			.append(ChatColorType.NORMAL)
			.append("the search panel now shows the GE price for items it used to mark as not on "
				+ "GE (the buy limit data it checked doesn't cover every GE item).")
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
		configManager.setConfiguration(ItemIdConfig.GROUP, K_ANNOUNCED, PLUGIN_VERSION);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// "Open item search panel" acts as a button: opens the side panel, then unticks itself.
		if (ItemIdConfig.GROUP.equals(event.getGroup())
			&& "openSearchPanel".equals(event.getKey())
			&& config.openSearchPanel()
			&& navButton != null)
		{
			clientToolbar.openPanel(navButton);
			configManager.setConfiguration(ItemIdConfig.GROUP, "openSearchPanel", false);
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.itemIdOnHover())
		{
			return;
		}
		final MenuEntry entry = event.getMenuEntry();
		final int id = itemId(entry);
		if (id <= 0)
		{
			return;
		}
		entry.setTarget(withId(entry.getTarget(), id));
	}

	/**
	 * The item id an entry refers to, or -1 if it isn't an item. Inventory, bank and worn items
	 * report it via {@link MenuEntry#getItemId()}; ground items carry it in the identifier instead.
	 */
	private static int itemId(MenuEntry entry)
	{
		final int id = entry.getItemId();
		if (id > 0)
		{
			return id;
		}
		switch (entry.getType())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return entry.getIdentifier();
			default:
				return -1;
		}
	}

	/** Appends " (id)" to a menu target, leaving empty targets and non-items untouched. */
	static String withId(String target, int id)
	{
		if (target == null || target.isEmpty() || id <= 0)
		{
			return target;
		}
		return target + ID_COLOR + " (" + id + ")";
	}

	/** A small magnifying-glass icon for the side-panel toolbar button. */
	private static BufferedImage navIcon()
	{
		final BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(220, 220, 220));
		g.setStroke(new BasicStroke(2f));
		g.drawOval(4, 4, 11, 11);
		g.drawLine(14, 14, 20, 20);
		g.dispose();
		return img;
	}
}
