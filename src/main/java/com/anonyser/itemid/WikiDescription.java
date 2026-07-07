package com.anonyser.itemid;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches a short item description from the OSRS wiki (the intro of the item's page) through the
 * MediaWiki API. A request is made only when an item is clicked, and each item's result is cached
 * for the session. URL building and JSON parsing are pure and unit-tested; the network call is
 * async and never blocks the UI. A failed or empty lookup just leaves the description off.
 */
class WikiDescription
{
	private static final HttpUrl API = HttpUrl.get("https://oldschool.runescape.wiki/api.php");

	interface Callback
	{
		void onResult(int id, String description);
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Map<Integer, String> cache = new ConcurrentHashMap<>();

	WikiDescription(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	void fetch(int id, String itemName, Callback cb)
	{
		final String cached = cache.get(id);
		if (cached != null)
		{
			cb.onResult(id, cached);
			return;
		}
		final Request request = new Request.Builder().url(url(itemName)).build();
		http.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// a lookup failure isn't worth surfacing; the description just stays off
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (body != null)
					{
						final String desc = parse(gson, body.string());
						if (desc != null)
						{
							cache.put(id, desc);
							cb.onResult(id, desc);
						}
					}
				}
				catch (Exception e)
				{
					// ignore malformed responses
				}
			}
		});
	}

	static HttpUrl url(String itemName)
	{
		return API.newBuilder()
			.addQueryParameter("action", "query")
			.addQueryParameter("format", "json")
			.addQueryParameter("prop", "extracts")
			.addQueryParameter("exintro", "1")
			.addQueryParameter("explaintext", "1")
			.addQueryParameter("exsentences", "3")
			.addQueryParameter("redirects", "1")
			.addQueryParameter("titles", itemName)
			.build();
	}

	/** Pulls the first page's plaintext extract out of a MediaWiki query response, or null. */
	static String parse(Gson gson, String json)
	{
		try
		{
			final JsonObject root = gson.fromJson(json, JsonObject.class);
			final JsonObject query = root != null ? root.getAsJsonObject("query") : null;
			final JsonObject pages = query != null ? query.getAsJsonObject("pages") : null;
			if (pages == null)
			{
				return null;
			}
			for (Map.Entry<String, JsonElement> e : pages.entrySet())
			{
				final JsonObject page = e.getValue().getAsJsonObject();
				if (page.has("extract"))
				{
					final String extract = page.get("extract").getAsString();
					if (extract != null && !extract.trim().isEmpty())
					{
						return extract.trim();
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			// unexpected shape -> no description
		}
		return null;
	}
}
