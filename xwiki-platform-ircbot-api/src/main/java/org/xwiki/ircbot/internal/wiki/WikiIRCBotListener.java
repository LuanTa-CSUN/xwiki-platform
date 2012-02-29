/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.ircbot.internal.wiki;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.ircbot.IRCBot;
import org.xwiki.ircbot.IRCBotException;
import org.xwiki.ircbot.IRCBotListener;
import org.xwiki.ircbot.wiki.WikiIRCBotConstants;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.Transformation;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationException;

public class WikiIRCBotListener implements IRCBotListener, WikiIRCBotConstants
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WikiIRCBotListener.class);

    private BotListenerData listenerData;

    private Map<String, XDOM> events;

    private Syntax syntax;

    private Transformation macroTransformation;

    private BlockRenderer plainTextBlockRenderer;

    private IRCBot bot;

    public WikiIRCBotListener(BotListenerData listenerData, Map<String, XDOM> events, Syntax syntax,
        Transformation macroTransformation, BlockRenderer plainTextBlockRenderer, IRCBot bot)
    {
        this.listenerData = listenerData;
        this.events = events;
        this.syntax = syntax;
        this.macroTransformation = macroTransformation;
        this.plainTextBlockRenderer = plainTextBlockRenderer;
        this.bot = bot;
    }

    @Override
    public String getName()
    {
        return this.listenerData.getName();
    }

    @Override
    public String getDescription()
    {
        return this.listenerData.getDescription();
    }

    @Override
    public void onConnect()
    {
        executeScript(ON_CONNECT_EVENT_NAME);
    }

    @Override
    public void onDisconnect()
    {
        executeScript(ON_DISCONNECT_EVENT_NAME);
    }

    @Override
    public void onJoin(String channel, String sender, String login, String hostname)
    {
        executeScript(ON_JOIN_EVENT_NAME);
    }

    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message)
    {
        executeScript(ON_MESSAGE_EVENT_NAME);
    }

    @Override
    public void onNickChange(String oldNick, String login, String hostname, String newNick)
    {
        executeScript(ON_NICK_CHANGE_EVENT_NAME);
    }

    @Override
    public void onPart(String channel, String sender, String login, String hostname)
    {
        executeScript(ON_PART_EVENT_NAME);
    }

    @Override
    public void onPrivateMessage(String sender, String login, String hostname, String message)
    {
        executeScript(ON_PRIVATE_MESSAGE_EVENT_NAME);
    }

    @Override
    public void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason)
    {
        executeScript(ON_QUIT_EVENT_NAME);
    }

    /**
     * Execute the Wiki bot listener written in wiki syntax by executing the Macros and send the result to the IRC
     * server.
     *
     * @param eventName the name of the event for which the script needs to be executed
     */
    private void executeScript(String eventName)
    {
        XDOM xdom = this.events.get(eventName);
        if (xdom != null) {
            // Note that if a Bot Listener script needs access to the IRC Bot (for example to send a message to the
            // IRC channel), it can access it through the "ircbot" Script Service.

            // Execute the Macro Transformation on XDOM and send the result to the IRC server
            TransformationContext txContext = new TransformationContext(xdom, this.syntax);
            try {
                this.macroTransformation.transform(xdom, txContext);
            } catch (TransformationException e) {
                // The transformation failed to execute, log an error and continue
                LOGGER.error("Failed to render Wiki IRC Bot Listener script", e);
                return;
            }
            DefaultWikiPrinter printer = new DefaultWikiPrinter();
            this.plainTextBlockRenderer.render(xdom, printer);

            try {
                this.bot.sendMessage(printer.toString());
            } catch (IRCBotException e) {
                // The bot is not connected to a channel yet, don't do anything
                LOGGER.warn(String.format("Failed to send message to the IRC server. Reason: [%s]", e.getMessage()));
            }
        }
    }
}
