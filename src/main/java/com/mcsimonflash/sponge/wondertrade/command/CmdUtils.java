package com.mcsimonflash.sponge.wondertrade.command;

import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

public class CmdUtils {

    public static Player requirePlayer(CommandSource src) throws CommandException {
        if (src instanceof Player) {
            return (Player) src;
        }
        throw new CommandException(WonderTrade.getMessage(src, "wondertrade.command.player-only"));
    }

    public static Text usage(String base, String description, Text... args) {
        return Text.builder(base)
                .color(TextColors.LIGHT_PURPLE)
                .onClick(TextActions.suggestCommand(base))
                .onHover(TextActions.showText(Text.of(TextColors.GRAY, description)))
                .append(Text.joinWith(Text.of(" "), args))
                .build();
    }

    public static Text arg(boolean req, String name, String desc) {
        return Text.builder((req ? "<" : "[") + name + (req ? ">" : "]"))
                .color(TextColors.DARK_PURPLE)
                .onHover(TextActions.showText(Text.of(TextColors.GRAY, desc)))
                .build();
    }

}
