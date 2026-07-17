package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Client-side service for collecting mod and resource pack data
 * and sending it to the server for validation
 */
public class ClientDataService {

    /**
     * Collect the client's mod list and active resource packs, then send to server
     * This method is called on initial server join and when resource packs are reloaded
     */
    public static void collectAndSendData() {
        try {
            FairPlayFairRule.LOGGER.info("Collecting client data (mods and resource packs)...");

            // Collect mod list in format "modId@version"
            List<String> modList = ModList.get().getMods().stream()
                    .map(modInfo -> modInfo.getModId() + "@" + modInfo.getVersion().toString())
                    .collect(Collectors.toCollection(ArrayList::new));

            FairPlayFairRule.LOGGER.info("Collected {} mods", modList.size());

            // Collect active resource pack list
            Minecraft minecraft = Minecraft.getInstance();
            List<String> resourcePackList = new ArrayList<>();

            if (minecraft.getResourcePackRepository() != null) {
                resourcePackList = minecraft.getResourcePackRepository()
                        .getSelectedPacks()
                        .stream()
                        .map(Pack::getId)
                        .collect(Collectors.toCollection(ArrayList::new));

                FairPlayFairRule.LOGGER.info("Collected {} active resource packs", resourcePackList.size());
            } else {
                FairPlayFairRule.LOGGER.warn("Resource pack repository is null!");
            }

            ClientPacketSender.sendToServer(new PacketHandler.ClientInfoPacket(modList, resourcePackList));

            FairPlayFairRule.LOGGER.info("Client data sent to server successfully");

        } catch (Exception e) {
            FairPlayFairRule.LOGGER.error("Error collecting/sending client data", e);
        }
    }
}
