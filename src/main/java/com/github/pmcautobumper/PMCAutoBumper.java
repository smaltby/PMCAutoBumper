package com.github.pmcautobumper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.logging.LogFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class PMCAutoBumper extends JavaPlugin
{
	WebClient webClient;

	public void onEnable()
	{
		//Enable web client with desired settings
		enableWebClient();

		saveDefaultConfig();

		if(getConfig().getBoolean("autobump"))
		{
			Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable()
			{
				@Override
				public void run()
				{
					attemptBump();
				}
			}, 10L, TimeUnit.MINUTES.toSeconds(30) * 20);
		}
	}

	public boolean onCommand(final CommandSender sender, Command cmd, String label, String[] args)
	{
		if (cmd.getName().equalsIgnoreCase("bump"))
		{
			if(!sender.hasPermission("pmcautobumper.admin"))
			{
				sender.sendMessage(ChatColor.RED+"You do not have permission to do this.");
				return true;
			}
			if(args.length > 1)
			{
				if(args[0].equalsIgnoreCase("reload"))
				{
					reloadConfig();
					sender.sendMessage(ChatColor.GREEN+"PMCAutoBumper config sucessfully reloaded.");
					return true;
				} else
				{
					return false;
				}
			}
			Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable()
			{
				@Override
				public void run()
				{
					bumpWithConfigSettings(sender);
				}
			});
		}

		return true;
	}

	public void attemptBump()
	{
		getLogger().log(Level.INFO, "Checking to see if the server can be bumped....");
		long lastBump = getConfig().getLong("last-bump", 0);
		long lastBumpHours = TimeUnit.MILLISECONDS.toHours(lastBump);
		long currentTimeHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis());
		if(currentTimeHours - lastBumpHours >= 24)
		{
			getLogger().log(Level.INFO, "The server does require bumping.");
			if(bumpWithConfigSettings(Bukkit.getConsoleSender()))
			{
				getConfig().set("last-bump", System.currentTimeMillis());
				saveConfig();
			}
		} else
		{
			getLogger().log(Level.INFO, "The server does not require bumping.");
		}
	}

	private boolean bumpWithConfigSettings(CommandSender sender)
	{
		return bump(sender, getConfig().getString("username"), getConfig().getString("password"), getConfig().getString("server-page"));
	}

	/**
	 * Logs into PMC with the defined username and password, and attempts to bump the defined server page.
	 * It is HIGHLY reccomended that you run this method asynchronously, as it WILL freeze the thread it's running in.
	 * @param sender		CommandSender to send updates to, may be null
	 * @param username		username to log in with
	 * @param password		password to log in with
	 * @param serverPage	server page to attempt to bump
	 * @return				whether or not the bump suceeded
	 */
	public boolean bump(CommandSender sender, String username, String password, String serverPage)
	{
		if(sender != null)
			sender.sendMessage(ChatColor.GREEN+"Attempting to bump server...");

		HtmlPage page;
		try
		{
			page = webClient.getPage("http://www.planetminecraft.com/account/sign_in/");
		} catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}

		if(page.getTitleText().matches(".*Website is currently unreachable.*"))
		{
			if(sender != null)
				sender.sendMessage(ChatColor.RED+"PMC is currently offline.");
			return false;
		}

		if(sender != null)
			sender.sendMessage(ChatColor.GREEN+"Connected to planet minecraft.");

		try
		{
			HtmlForm form = page.getFirstByXPath("/html/body//div[@class='half']/form");
			HtmlElement usernameElement = form.getInputByName("username");
			HtmlElement passwordElement = form.getInputByName("password");
			HtmlElement loginElement = form.getInputByName("login");

			usernameElement.type(username);
			passwordElement.type(password);
			page = loginElement.click();
		} catch(Exception e)
		{
			e.printStackTrace();
		}

		HtmlElement errorElement = page.getFirstByXPath("/html/body//div[@class='error']");
		if(errorElement != null)
		{
			if(sender != null)
			{
				sender.sendMessage(ChatColor.RED+"Login failed.");
				sender.sendMessage(ChatColor.RED+errorElement.getTextContent());
			}
			return false;
		}

		if(sender != null)
			sender.sendMessage(ChatColor.GREEN+"Logged into planet minecraft.");

		String serverId = serverPage.replaceAll("\\D+", "");

		try
		{
			page = webClient.getPage("http://www.planetminecraft.com/account/manage/servers/"+serverId+"/#tab_log");
		} catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}

		if(sender != null)
			sender.sendMessage(ChatColor.GREEN+"Navigated to update page.");

		try
		{
			HtmlElement bumpElement = (HtmlElement) page.getElementById("bump");
			bumpElement.click();
			if(sender != null)
				sender.sendMessage(ChatColor.GREEN+"Clicked bump button.");
		} catch(Exception e)
		{
			if(sender != null)
				sender.sendMessage(ChatColor.RED+"Could not bump server, you have bumped the server sometime in the past 24 hours.");
			return false;
		}

		if(sender != null)
			sender.sendMessage(ChatColor.GREEN+"Server sucessfully bumped!");
		return true;
	}

	private void enableWebClient()
	{
		//Arbitrary choice of browser
		webClient = new WebClient(BrowserVersion.FIREFOX_17);
		//This gives time for the javascript to load. If we don't allow it to load, clicking the bump button fails
		webClient.setAjaxController(new NicelyResynchronizingAjaxController());
		//Since we're giving time for javascript to load, we obviously want javascript enabled as well
		webClient.getOptions().setJavaScriptEnabled(true);
		//May or may not be necessary
		webClient.getOptions().setCssEnabled(true);
		webClient.getOptions().setRedirectEnabled(true);
		//PMC is apparently poorly designed, as HTMLUnit complains quite loudly about the site unless we tell it to shut up
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
		java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
		java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		webClient.getOptions().setThrowExceptionOnScriptError(false);
	}
}
