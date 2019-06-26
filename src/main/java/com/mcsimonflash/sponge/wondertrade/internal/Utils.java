package com.mcsimonflash.sponge.wondertrade.internal;

import com.google.common.base.Preconditions;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.enums.DeleteType;
import com.pixelmonmod.pixelmon.api.enums.ReceiveType;
import com.pixelmonmod.pixelmon.api.events.PixelmonDeletedEvent;
import com.pixelmonmod.pixelmon.api.events.PixelmonReceivedEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.translation.locale.Locales;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static World world;
    public static final UUID ZERO_UUID = new UUID(0, 0);
    private static final Pattern MESSAGE = Pattern.compile("\\[(.+?)]\\(((?:.|\n)+?)\\)");

    public static void initialize() {
        world = (net.minecraft.world.World) Sponge.getServer().getWorld(Sponge.getServer().getDefaultWorldName())
                .orElseThrow(() -> new IllegalStateException("No default world."));
        Config.load();
        Manager.fillPool(false, true);
    }

    public static Text toText(String msg) {
        return TextSerializers.FORMATTING_CODE.deserialize(msg);
    }

    public static Text parseText(String message) {
        Matcher matcher = MESSAGE.matcher(message);
        Text.Builder builder = Text.builder();
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() > index) {
                builder.append(toText(message.substring(index, matcher.start())));
            }
            Text.Builder subtext = toText(matcher.group(1)).toBuilder();
            String group = matcher.group(2);
            try {
                subtext.onClick(group.startsWith("/") ? TextActions.runCommand(group) : TextActions.openUrl(new URL(group)));
                subtext.onHover(TextActions.showText(Text.of(group)));
            } catch (MalformedURLException e) {
                subtext.onHover(TextActions.showText(toText(group)));
            }
            builder.append(subtext.build());
            index = matcher.end();
            if (matcher.hitEnd() && index < message.length()) {
                builder.append(toText(message.substring(index)));
            }
        }
        if (index == 0) {
            builder.append(toText(message));
        }
        return builder.toText();
    }

    public static PlayerPartyStorage getPartyStorage(Player player) {
        return Pixelmon.storageManager.getParty(player.getUniqueId());
        //return PixelmonStorage.pokeBallManager.getPlayerStorage((EntityPlayerMP) player).orElseThrow(() -> new IllegalStateException("No player storage."));
    }

    public static PCStorage getPcStorage(Player player) {
        return Pixelmon.storageManager.getPCForPlayer(player.getUniqueId());
        //return PixelmonStorage.computerManager.getPlayerStorage((EntityPlayerMP) player);
    }

    public static long getCooldown(Player player) {
        try {
            return Integer.parseInt(player.getOption("wondertrade:cooldown").orElse(String.valueOf(Config.defCooldown)));
        } catch (NumberFormatException e) {
            WonderTrade.getLogger().error("Malformatted cooldown option set for player " + player.getName() + ": " + player.getOption("wondertrade:cooldown").orElse(""));
            return Config.defCooldown;
        }
    }

    public static void trade(Player player, int slot) {
        PlayerPartyStorage storage = getPartyStorage(player);
        storage.retrieveAll();

        Pokemon pokemon = storage.get(slot);

        TradeEntry entry = trade(player, pokemon);

        storage.set(slot, null);

        Pixelmon.EVENT_BUS.post(new PixelmonDeletedEvent((EntityPlayerMP) player, pokemon, DeleteType.COMMAND));
        storage.add(entry.getPokemon());
        Pixelmon.EVENT_BUS.post(new PixelmonReceivedEvent((EntityPlayerMP) player, ReceiveType.Command, pokemon));

        storage.updatePlayer();
    }

    public static void trade(Player player, int boxPos, int pos) {
        PCStorage storage = getPcStorage(player);
        PCBox box = storage.getBox(boxPos);
        Pokemon pokemon = box.get(pos);
        //TradeEntry entry = trade(player, pokemon);
        trade(player, pokemon);
        if (pokemon == null) return;
        //box.get(pos).set

        //box.changePokemon(pos, entry.getPokemon().serializeNBT());
        box.set(pos, pokemon);

        Pixelmon.EVENT_BUS.post(new PixelmonDeletedEvent((EntityPlayerMP) player, pokemon, DeleteType.COMMAND));
        Pixelmon.EVENT_BUS.post(new PixelmonReceivedEvent((EntityPlayerMP) player, ReceiveType.Command, pokemon));
    }

    private static TradeEntry trade(Player player, Pokemon pokemon) {
        Preconditions.checkArgument(Config.allowEggs || !pokemon.isEgg(), WonderTrade.getMessage(player.getLocale(), "wondertrade.trade.no-eggs"));
        TradeEntry entry = new TradeEntry(pokemon, player.getUniqueId(), LocalDateTime.now());
        logTransaction(player, entry, true);
        entry = Manager.trade(entry).refine(player);
        logTransaction(player, entry, false);
        Object[] args = new Object[] {"player", player.getName(), "traded", getShortDesc(pokemon), "traded-details", getDesc(pokemon), "received", getShortDesc(entry.getPokemon()), "received-details", getDesc(entry.getPokemon())};
        if (Config.broadcastTrades && (pokemon.isShiny() || EnumSpecies.legendaries.contains(pokemon.getSpecies().name))) {
            Sponge.getServer().getBroadcastChannel().send(WonderTrade.getPrefix().concat(parseText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.trade.success.broadcast", args).toString())));
        } else {
            player.sendMessage(WonderTrade.getPrefix().concat(parseText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.trade.success.message", args).toString())));
        }
        return entry;
    }

    public static void take(Player player, int index) {
        PlayerPartyStorage storage = getPartyStorage(player);
        storage.retrieveAll();
        TradeEntry entry = Manager.take(index).refine(player);
        logTransaction(player, entry, false);
        storage.add(entry.getPokemon());
    }

    public static void logTransaction(User user, TradeEntry entry, boolean add) {
        WonderTrade.getLogger().info(user.getName() + (add ? " added " : " removed ") + " a " + getShortDesc(entry.getPokemon()) + (add ? "." : " (added by " + TextSerializers.FORMATTING_CODE.stripCodes(entry.getOwnerName()) + ")."));
    }

    public static String getShortDesc(Pokemon pokemon) {
        return pokemon.isEgg() ? "mysterious egg" : "level " + pokemon.getLevel() + (pokemon.isShiny() ? " shiny " : " ") + (EnumSpecies.legendaries.contains(pokemon.getSpecies().name) ? "legendary " : "") + pokemon.getSpecies().name;
    }

    public static String getDesc(Pokemon pokemon) {
        if (pokemon.isEgg()) {
            return "&7Pokemon&8: &6???";
        }
        StringBuilder builder = new StringBuilder("&7Pokemon&8: &6").append(pokemon.getSpecies().name);
        //if (pokemon.getItemHeld() != NoItem.noItem) {
        if (!pokemon.getHeldItem().isEmpty()) {
            builder.append("\n&7Held Item&7: &6").append(pokemon.getHeldItem().getDisplayName());
        }
        builder.append("\n&7Ability&8: &6").append(pokemon.getAbility().getName())
                .append("\n&7Level&8: &6").append(pokemon.getLevel())
                .append("\n&7Shiny&8: &6").append(pokemon.isShiny() ? "&aYes" : "&cNo")
                .append("\n&7EVs&8: &d")
                .append(pokemon.getStats().evs.hp).append("&5/&d")
                .append(pokemon.getStats().evs.attack).append("&5/&d")
                .append(pokemon.getStats().evs.defence).append("&5/&d")
                .append(pokemon.getStats().evs.specialAttack).append("&5/&d")
                .append(pokemon.getStats().evs.specialDefence).append("&5/&d")
                .append(pokemon.getStats().evs.speed)
                .append("\n&7IVs&8: &d")
                .append(pokemon.getStats().ivs.hp).append("&5/&d")
                .append(pokemon.getStats().ivs.attack).append("&5/&d")
                .append(pokemon.getStats().ivs.defence).append("&5/&d")
                .append(pokemon.getStats().ivs.specialAttack).append("&5/&d")
                .append(pokemon.getStats().ivs.specialDefence).append("&5/&d")
                .append(pokemon.getStats().ivs.speed);
        return builder.toString();
    }

    public static World getWorld() {
        return world;
    }

}