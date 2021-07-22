package ac.grim.grimac.predictionengine.movementTick;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntityRideable;
import org.apache.commons.lang.NotImplementedException;

public class MovementTickerRideable extends MovementTickerLivingVehicle {

    public MovementTickerRideable(GrimPlayer player) {
        super(player);

        // If the player has carrot/fungus on a stick, otherwise the player has no control
        float f = getSteeringSpeed();

        PacketEntityRideable boost = ((PacketEntityRideable) player.playerVehicle);
        // Do stuff for boosting on a pig/strider
        if (boost.currentBoostTime++ < boost.boostTimeMax) {
            // I wonder how much fastmath actually affects boosting movement
            f += f * 1.15F * player.trigHandler.sin((float) boost.currentBoostTime / (float) boost.boostTimeMax * (float) Math.PI);
        }

        player.speed = f;
        setMovementSpeed();
    }

    public void setMovementSpeed() {
        player.movementSpeed = player.speed;
    }

    // Pig and Strider should implement this
    public float getSteeringSpeed() {
        throw new NotImplementedException();
    }
}
