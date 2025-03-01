package ac.grim.grimac.events.packets.worldreader;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.chunkdata.BaseChunk;
import ac.grim.grimac.utils.chunkdata.twelve.TwelveChunk;
import ac.grim.grimac.utils.chunks.Column;
import ac.grim.grimac.utils.data.ChangeBlockData;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packetwrappers.NMSPacket;
import io.github.retrooper.packetevents.packetwrappers.WrappedPacket;
import io.github.retrooper.packetevents.packetwrappers.play.out.mapchunk.WrappedPacketOutMapChunk;
import io.github.retrooper.packetevents.utils.reflection.Reflection;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.BitSet;

public class PacketWorldReaderEight extends BasePacketWorldReader {
    // Synchronous
    private void readChunk(ShortBuffer buf, BaseChunk[] chunks, BitSet set) {
        // We only need block data!
        for (int ind = 0; ind < 16; ind++) {
            if (set.get(ind)) {
                TwelveChunk compressed = new TwelveChunk(buf);
                chunks[ind] = compressed;
            }
        }
    }

    @Override
    public void handleMapChunkBulk(GrimPlayer player, PacketPlaySendEvent event) {
        WrappedPacket packet = new WrappedPacket(event.getNMSPacket());
        int[] chunkXArray = packet.readIntArray(0);
        int[] chunkZArray = packet.readIntArray(1);
        Object[] chunkData = (Object[]) packet.readAnyObject(2);

        for (int i = 0; i < chunkXArray.length; i++) {
            BaseChunk[] chunks = new BaseChunk[16];
            int chunkX = chunkXArray[i];
            int chunkZ = chunkZArray[i];

            WrappedPacket nmsChunkMapWrapper = new WrappedPacket(new NMSPacket(chunkData[i]));
            ShortBuffer buf = ByteBuffer.wrap(nmsChunkMapWrapper.readByteArray(0)).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

            readChunk(buf, chunks, BitSet.valueOf(new long[]{nmsChunkMapWrapper.readInt(0)}));

            Column column = new Column(chunkX, chunkZ, chunks, player.lastTransactionSent.get() + 1);
            player.compensatedWorld.addToCache(column, chunkX, chunkZ);
        }
    }

    @Override
    public void handleMapChunk(GrimPlayer player, PacketPlaySendEvent event) {
        WrappedPacketOutMapChunk packet = new WrappedPacketOutMapChunk(event.getNMSPacket());

        try {
            int chunkX = packet.getChunkX();
            int chunkZ = packet.getChunkZ();

            // Map chunk packet with 0 sections and continuous chunk is the unload packet in 1.7 and 1.8
            // Optional is only empty on 1.17 and above
            Object chunkMap = packet.readAnyObject(2);
            if (chunkMap.getClass().getDeclaredField("b").getInt(chunkMap) == 0 && packet.isGroundUpContinuous().get()) {
                unloadChunk(player, chunkX, chunkZ);
                return;
            }

            ShortBuffer buf = ByteBuffer.wrap(packet.getCompressedData()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            BaseChunk[] chunks = new BaseChunk[16];
            BitSet set = packet.getBitSet();

            readChunk(buf, chunks, set);

            addChunkToCache(player, chunks, packet.isGroundUpContinuous().get(), chunkX, chunkZ);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleMultiBlockChange(GrimPlayer player, PacketPlaySendEvent event) {
        WrappedPacket packet = new WrappedPacket(event.getNMSPacket());

        try {
            // Section Position or Chunk Section - depending on version
            Object position = packet.readAnyObject(0);

            Object[] blockInformation;
            blockInformation = (Object[]) packet.readAnyObject(1);

            // This shouldn't be possible
            if (blockInformation.length == 0) return;

            Field getX = position.getClass().getDeclaredField("x");
            Field getZ = position.getClass().getDeclaredField("z");

            int chunkX = getX.getInt(position) << 4;
            int chunkZ = getZ.getInt(position) << 4;

            Field shortField = Reflection.getField(blockInformation[0].getClass(), 0);
            Field blockDataField = Reflection.getField(blockInformation[0].getClass(), 1);

            int range = (player.getTransactionPing() / 100) + 32;
            if (Math.abs(chunkX - player.x) < range && Math.abs(chunkZ - player.z) < range)
                event.setPostTask(player::sendTransaction);


            for (Object o : blockInformation) {
                short pos = shortField.getShort(o);
                int blockID = getByCombinedID(blockDataField.get(o));

                int blockX = pos >> 12 & 15;
                int blockY = pos & 255;
                int blockZ = pos >> 8 & 15;

                player.compensatedWorld.worldChangedBlockQueue.add(new ChangeBlockData(player.lastTransactionSent.get() + 1, chunkX + blockX, blockY, chunkZ + blockZ, blockID));
            }

        } catch (IllegalAccessException | NoSuchFieldException exception) {
            exception.printStackTrace();
        }
    }
}
