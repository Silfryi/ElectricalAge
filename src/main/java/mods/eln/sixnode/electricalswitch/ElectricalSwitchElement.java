package mods.eln.sixnode.electricalswitch;

import mods.eln.Eln;
import mods.eln.i18n.I18N;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElement;
import mods.eln.sim.mna.state.ElectricalLoad;
import mods.eln.sim.nbt.NbtResistorSwitch;
import mods.eln.sim.thermal.ThermalLoad;
import mods.eln.sim.mna.passive.Resistor;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.sim.process.destruct.VoltageStateWatchDog;
import mods.eln.sim.process.destruct.WorldExplosion;
import mods.eln.sound.SoundCommand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ElectricalSwitchElement extends SixNodeElement {

    public ElectricalSwitchDescriptor descriptor;
    public NbtElectricalLoad aLoad = new NbtElectricalLoad("aLoad");
    public NbtElectricalLoad bLoad = new NbtElectricalLoad("bLoad");
    public NbtResistorSwitch switchResistor = new NbtResistorSwitch("switchRes", aLoad, bLoad);

    VoltageStateWatchDog voltageWatchDogA = new VoltageStateWatchDog();
    VoltageStateWatchDog voltageWatchDogB = new VoltageStateWatchDog();
//	ResistorCurrentWatchdog currentWatchDog = new ResistorCurrentWatchdog();

    boolean switchState = false;

    public ElectricalSwitchElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);

        switchResistor.mustUseUltraImpedance();
        electricalLoadList.add(aLoad);
        electricalLoadList.add(bLoad);
        electricalComponentList.add(switchResistor);
        Resistor r1 = new Resistor(bLoad, null);
        r1.pullDown();
        electricalComponentList.add(r1);
        Resistor r2 = new Resistor(aLoad, null);
        r2.pullDown();
        electricalComponentList.add(r2);

        this.descriptor = (ElectricalSwitchDescriptor) descriptor;

        WorldExplosion exp = new WorldExplosion(this).cableExplosion();

        //	slowProcessList.add(currentWatchDog);
        slowProcessList.add(voltageWatchDogA);
        slowProcessList.add(voltageWatchDogB);

        //currentWatchDog.set(switchResistor).setIAbsMax(this.descriptor.maximalPower/this.descriptor.nominalVoltage).set(exp);
        voltageWatchDogA.set(aLoad).setUNominal(this.descriptor.nominalVoltage).set(exp);
        voltageWatchDogB.set(bLoad).setUNominal(this.descriptor.nominalVoltage).set(exp);
    }

    public static boolean canBePlacedOnSide(Direction side, int type) {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
        switchState = nbt.getBoolean("switchState");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("front", (byte) (front.toInt() << 0));
        nbt.setBoolean("switchState", switchState);
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (front == lrdu) return aLoad;
        if (front.inverse() == lrdu) return bLoad;
        return null;
    }

    @Override
    public ThermalLoad getThermalLoad(LRDU lrdu, int mask) {
        //return thermalLoad;
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (front == lrdu) return descriptor.getNodeMask();
        if (front.inverse() == lrdu) return descriptor.getNodeMask();

        return 0;
    }

    @Override
    public String multiMeterString() {
        return Utils.plotVolt("Ua:", aLoad.getU()) + Utils.plotVolt("Ub:", bLoad.getU()) + Utils.plotAmpere("I:", aLoad.getCurrent());
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Position"), switchState ? I18N.tr("Closed") : I18N.tr("Open"));
        info.put(I18N.tr("Current"), Utils.plotAmpere("", aLoad.getCurrent()));
        if (Eln.wailaEasyMode) {
            info.put(I18N.tr("Voltages"), Utils.plotVolt("", aLoad.getU()) + Utils.plotVolt(" ", bLoad.getU()));
        }

        try {
            int subSystemSize = switchResistor.getSubSystem().matrixSize();
            String textColor = "";
            if (subSystemSize <= 8) {
                textColor = "§a";
            } else if (subSystemSize <= 15) {
                textColor = "§6";
            } else {
                textColor = "§c";
            }
            info.put(I18N.tr("Subsystem Matrix Size: "), textColor + subSystemSize);


        } catch (Exception e) {
            info.put(I18N.tr("Subsystem Matrix Size: "), "§cNot part of a subsystem!?");
        }

        return info;
    }

    @Override
    public String thermoMeterString() {
        //return Utils.plotCelsius("T:",thermalLoad.Tc);
        return "";
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeBoolean(switchState);
            stream.writeShort((short) (aLoad.getU() * NodeBase.NETWORK_SERIALIZE_U_FACTOR));
            stream.writeShort((short) (bLoad.getU() * NodeBase.NETWORK_SERIALIZE_U_FACTOR));
            stream.writeShort((short) (aLoad.getCurrent() * NodeBase.NETWORK_SERIALIZE_I_FACTOR));
            //stream.writeShort((short)(thermalLoad.Tc * NodeBase.NETWORK_SERIALIZE_T_FACTOR));
            stream.writeShort(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSwitchState(boolean state) {
        switchState = state;
        switchResistor.setState(state);
        needPublish();
    }

    @Override
    public void initialize() {
        //descriptor.thermal.applyTo(thermalLoad);

        descriptor.applyTo(aLoad);
        descriptor.applyTo(bLoad);

        switchResistor.setR(descriptor.electricalRs);

        setSwitchState(switchState);
    }

    @Override
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
        if (onBlockActivatedRotate(entityPlayer)) return true;
        ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();

        if (Eln.multiMeterElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem())) {
            return false;
        }
        if (Eln.thermometerElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem())) {
            return false;
        }
        if (Eln.allMeterElement.checkSameItemStack(entityPlayer.getCurrentEquippedItem())) {
            return false;
        } else {
            setSwitchState(!switchState);
            //playSoundEffect("random.click", 0.3F, 0.6F);
            play(new SoundCommand("random.click").mulVolume(0.3F, 0.6f).smallRange());
            return true;
        }
        //front = LRDU.fromInt((front.toInt()+1)&3);
    }
}
