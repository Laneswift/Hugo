package com.hugosmp.auktion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single-File Mod for Hugo SMP Event / Auction GUI
 * Target Loader: Fabric (1.20.1)
 */
public class HugoAuktionMod implements ClientModInitializer {

    public static KeyBinding openGuiKey;
    public static Map<String, Double> donations = new HashMap<>();
    public static long timerStartTime = 0;
    public static boolean timerRunning = false;
    public static final int TIMER_DURATION_SECONDS = 60;

    // Chat-Pattern für /pay Nachrichten
    // Je nach Plugin auf Hugo SMP eventuell anpassen!
    private static final Pattern PAY_PATTERN = Pattern.compile("^(?:\\w+ )?(\\w+) hat dir ([0-9]+(?:\\.[0-9]+)?)\\$? überwiesen");

    @Override
    public void onInitializeClient() {
        // Tastenbelegung (Standard: G)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hugosmp.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.hugosmp"
        ));

        // Key-Press Event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new AuctionScreen());
                }
            }
        });

        // Chat Listener liest /pay Nachrichten aus
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String plainText = message.getString();
            Matcher matcher = PAY_PATTERN.matcher(plainText);

            if (matcher.find()) {
                String player = matcher.group(1);
                double amount = Double.parseDouble(matcher.group(2));

                // Spende speichern & aufsummieren
                donations.put(player, donations.getOrDefault(player, 0.0) + amount);

                // Timer beim ersten Pay automatisch starten
                if (!timerRunning) {
                    timerStartTime = System.currentTimeMillis();
                    timerRunning = true;
                }
            }
        });
    }

    // Inner Class für das GUI-Menü
    public static class AuctionScreen extends Screen {

        public AuctionScreen() {
            super(Text.literal("Hugo SMP Event GUI"));
        }

        @Override
        protected void init() {
            super.init();

            // Button zum manuellen Starten/Zurücksetzen des Timers
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Timer Start / Reset"), button -> {
                HugoAuktionMod.timerStartTime = System.currentTimeMillis();
                HugoAuktionMod.timerRunning = true;
                HugoAuktionMod.donations.clear();
            }).dimensions(this.width / 2 - 150, this.height - 35, 140, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);

            MinecraftClient client = MinecraftClient.getInstance();

            // 1. Name des Spielers anzeigen
            if (client.player != null) {
                context.drawText(this.textRenderer, "Host: " + client.player.getName().getString(), 20, 20, 0xFFFFFF, true);
            }

            // 2. Item aus der Haupthand als Vorschau anzeigen
            if (client.player != null) {
                ItemStack heldItem = client.player.getMainHandStack();
                context.drawText(this.textRenderer, "Auktions-Item:", 20, 45, 0xAAAAAA, false);
                context.drawItem(heldItem, 20, 60); // Item Icon rendern
                context.drawText(this.textRenderer, heldItem.getName().getString(), 42, 65, 0xFFFF55, false);
            }

            // 3. Timer Berechnung
            int remainingSeconds = 0;
            if (HugoAuktionMod.timerRunning) {
                long elapsed = (System.currentTimeMillis() - HugoAuktionMod.timerStartTime) / 1000;
                remainingSeconds = Math.max(0, HugoAuktionMod.TIMER_DURATION_SECONDS - (int) elapsed);
                if (remainingSeconds == 0) {
                    HugoAuktionMod.timerRunning = false;
                }
            }

            context.drawText(this.textRenderer, "Zeit: " + remainingSeconds + "s", 20, 95, remainingSeconds < 10 ? 0xFF5555 : 0x55FF55, true);

            // 4. Gewinner / Spenden-Rangliste
            context.drawText(this.textRenderer, "--- RANGLISTE (/pay) ---", this.width / 2 + 10, 20, 0xFFFF55, true);

            List<Map.Entry<String, Double>> sortedList = HugoAuktionMod.donations.entrySet()
                    .stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .collect(Collectors.toList());

            int yOffset = 40;
            int rank = 1;

            for (Map.Entry<String, Double> entry : sortedList) {
                String line = "#" + rank + " " + entry.getKey() + ": " + entry.getValue() + "$";
                
                context.drawText(this.textRenderer, line, this.width / 2 + 10, yOffset, 0xFFFFFF, false);

                // Hover-Effekt für Klick-Aktion
                if (mouseX >= this.width / 2 + 10 && mouseX <= this.width / 2 + 200 && mouseY >= yOffset && mouseY <= yOffset + 10) {
                    context.drawText(this.textRenderer, " [TP Kopieren]", this.width / 2 + 130, yOffset, 0x55FFFF, false);
                }

                yOffset += 15;
                rank++;
                if (rank > 8) break; // Zeige Top 8
            }

            if (sortedList.isEmpty()) {
                context.drawText(this.textRenderer, "Noch keine Spenden empfangen...", this.width / 2 + 10, 40, 0x888888, false);
            }

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Klick auf den Namen kopiert den Teleport-Befehl
            int yOffset = 40;
            List<Map.Entry<String, Double>> sortedList = HugoAuktionMod.donations.entrySet()
                    .stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .toList();

            for (Map.Entry<String, Double> entry : sortedList) {
                if (mouseX >= this.width / 2 + 10 && mouseX <= this.width / 2 + 200 && mouseY >= yOffset && mouseY <= yOffset + 10) {
                    String targetPlayer = entry.getKey();
                    
                    // In Zwischenablage kopieren
                    MinecraftClient.getInstance().keyboard.setClipboard("/tp " + targetPlayer);
                    
                    if (client != null && client.player != null) {
                        client.player.sendMessage(Text.literal("§a[Auktion] '/tp " + targetPlayer + "' in die Zwischenablage kopiert!"), false);
                    }
                    return true;
                }
                yOffset += 15;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}