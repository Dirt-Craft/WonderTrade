package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Children;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.service.pagination.PaginationList;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Aliases({"wondertrade", "wtrade"})
@Children({Menu.class, Pool.class, Regen.class, Take.class, Trade.class})
public class Base extends Command {

    @Inject
    protected Base(Settings settings) {
        super(settings.usage(CmdUtils.usage("/wondertrade", "The base command for WonderTrade")));
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        PaginationList.builder()
                .title(Utils.toText("&cDirtCraft &dWonder&5Trade"))
                .padding(Utils.toText("&4&m-"))
                .contents(Stream.concat(Stream.of(getUsage()), ((Command) this).getChildren().stream().filter(c -> c.getSpec().testPermission(src)).map(Command::getUsage)).collect(Collectors.toList()))
                .sendTo(src);
        return CommandResult.success();
    }

}