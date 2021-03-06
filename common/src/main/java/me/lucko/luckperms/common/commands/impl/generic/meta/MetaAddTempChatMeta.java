/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.impl.generic.meta;

import me.lucko.luckperms.api.ChatMetaType;
import me.lucko.luckperms.api.DataMutateResult;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.context.MutableContextSet;
import me.lucko.luckperms.common.commands.ArgumentPermissions;
import me.lucko.luckperms.common.commands.CommandException;
import me.lucko.luckperms.common.commands.CommandResult;
import me.lucko.luckperms.common.commands.abstraction.SharedSubCommand;
import me.lucko.luckperms.common.commands.sender.Sender;
import me.lucko.luckperms.common.commands.utils.ArgumentUtils;
import me.lucko.luckperms.common.commands.utils.Util;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.constants.Constants;
import me.lucko.luckperms.common.constants.Permission;
import me.lucko.luckperms.common.core.NodeFactory;
import me.lucko.luckperms.common.core.TemporaryModifier;
import me.lucko.luckperms.common.core.model.PermissionHolder;
import me.lucko.luckperms.common.data.LogEntry;
import me.lucko.luckperms.common.locale.CommandSpec;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.utils.DateUtil;
import me.lucko.luckperms.common.utils.Predicates;

import net.kyori.text.Component;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.serializer.ComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetaAddTempChatMeta extends SharedSubCommand {
    private final ChatMetaType type;

    public MetaAddTempChatMeta(LocaleManager locale, ChatMetaType type) {
        super(
                type == ChatMetaType.PREFIX ? CommandSpec.META_ADDTEMP_PREFIX.spec(locale) : CommandSpec.META_ADDTEMP_SUFFIX.spec(locale),
                "addtemp" + type.name().toLowerCase(),
                type == ChatMetaType.PREFIX ? Permission.USER_META_ADDTEMP_PREFIX : Permission.USER_META_ADDTEMP_SUFFIX,
                type == ChatMetaType.PREFIX ? Permission.GROUP_META_ADDTEMP_PREFIX : Permission.GROUP_META_ADDTEMP_SUFFIX,
                Predicates.inRange(0, 2)
        );
        this.type = type;
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, PermissionHolder holder, List<String> args, String label, Permission permission) throws CommandException {
        if (ArgumentPermissions.checkModifyPerms(plugin, sender, permission, holder)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        int priority = ArgumentUtils.handlePriority(0, args);
        String meta = ArgumentUtils.handleString(1, args);
        long duration = ArgumentUtils.handleDuration(2, args);
        MutableContextSet context = ArgumentUtils.handleContext(3, args, plugin);
        TemporaryModifier modifier = plugin.getConfiguration().get(ConfigKeys.TEMPORARY_ADD_BEHAVIOUR);

        if (ArgumentPermissions.checkContext(plugin, sender, permission, context)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        Map.Entry<DataMutateResult, Node> ret = holder.setPermission(NodeFactory.makeChatMetaNode(type, priority, meta).setExpiry(duration).withExtraContext(context).build(), modifier);

        if (ret.getKey().asBoolean()) {
            duration = ret.getValue().getExpiryUnixTime();

            Component component = ComponentSerializer.parseFromLegacy(Message.ADD_TEMP_CHATMETA_SUCCESS.asString(plugin.getLocaleManager(), holder.getFriendlyName(), type.name().toLowerCase(), meta, priority, DateUtil.formatDateDiff(duration), Util.contextSetToString(context)), Constants.COLOR_CHAR);
            HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentSerializer.parseFromLegacy(
                    "¥3Raw " + type.name().toLowerCase() + ": ¥r" + meta,
                    '¥'
            ));
            component.applyRecursively(c -> c.hoverEvent(event));
            sender.sendMessage(component);

            LogEntry.build().actor(sender).acted(holder)
                    .action("meta addtemp" + type.name().toLowerCase() + " " + args.stream().map(ArgumentUtils.WRAPPER).collect(Collectors.joining(" ")))
                    .build().submit(plugin, sender);

            save(holder, sender, plugin);
            return CommandResult.SUCCESS;
        } else {
            Message.ALREADY_HAS_CHAT_META.send(sender, holder.getFriendlyName(), type.name().toLowerCase());
            return CommandResult.STATE_ERROR;
        }
    }
}
