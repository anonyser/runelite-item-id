package com.anonyser.itemid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combination recipes (a GE item built from other GE items), loaded by name from recipes.csv. The
 * data is only names; {@link #resolve} turns them into item ids using a name-to-id map built from the
 * game's own items, so an id is never guessed. A recipe whose result or any component can't be
 * resolved is dropped rather than shown wrong.
 */
class Recipes
{
	// result name (as written) -> component names
	private final Map<String, List<String>> byName = new LinkedHashMap<>();

	static Recipes load()
	{
		final Recipes r = new Recipes();
		try (InputStream in = Recipes.class.getResourceAsStream("/com/anonyser/itemid/recipes.csv"))
		{
			if (in != null)
			{
				try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
				{
					String line;
					while ((line = br.readLine()) != null)
					{
						r.add(line);
					}
				}
			}
		}
		catch (IOException e)
		{
			// leave whatever parsed
		}
		return r;
	}

	void add(String line)
	{
		if (line == null)
		{
			return;
		}
		final String trimmed = line.trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("|"))
		{
			return;
		}
		final String[] parts = trimmed.split("\\|");
		final String result = parts[0].trim();
		final List<String> components = new ArrayList<>();
		for (int i = 1; i < parts.length; i++)
		{
			final String c = parts[i].trim();
			if (!c.isEmpty())
			{
				components.add(c);
			}
		}
		if (!result.isEmpty() && !components.isEmpty())
		{
			byName.put(result, components);
		}
	}

	/**
	 * Resolves the loaded recipes to item ids using a lowercase-name to id map. Only recipes whose
	 * result and every component resolve are returned, keyed by the result item id.
	 */
	Map<Integer, int[]> resolve(Map<String, Integer> nameToId)
	{
		final Map<Integer, int[]> out = new HashMap<>();
		for (Map.Entry<String, List<String>> e : byName.entrySet())
		{
			final Integer resultId = nameToId.get(e.getKey().toLowerCase());
			if (resultId == null || resultId <= 0)
			{
				continue;
			}
			final List<String> names = e.getValue();
			final int[] ids = new int[names.size()];
			boolean ok = true;
			for (int i = 0; i < names.size(); i++)
			{
				final Integer cid = nameToId.get(names.get(i).toLowerCase());
				if (cid == null || cid <= 0)
				{
					ok = false;
					break;
				}
				ids[i] = cid;
			}
			if (ok)
			{
				out.put(resultId, ids);
			}
		}
		return out;
	}

	int size()
	{
		return byName.size();
	}
}
