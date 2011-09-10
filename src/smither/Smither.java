package smither;

import com.rsbuddy.event.events.MessageEvent;
import com.rsbuddy.event.listeners.MessageListener;
import com.rsbuddy.event.listeners.PaintListener;
import com.rsbuddy.script.ActiveScript;
import com.rsbuddy.script.Manifest;
import com.rsbuddy.script.methods.Menu;
import com.rsbuddy.script.methods.*;
import com.rsbuddy.script.task.Task;
import com.rsbuddy.script.util.Random;
import com.rsbuddy.script.util.Timer;
import com.rsbuddy.script.wrappers.GameObject;
import com.rsbuddy.script.wrappers.Widget;
import org.rsbuddy.tabs.Inventory;
import org.rsbuddy.widgets.Bank;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

@Manifest(authors = "programer", name = "Smither",
        keywords = "Smithing", version = 0.1, description = "Smiths platebodys")
public class Smither extends ActiveScript implements PaintListener, MouseListener, MessageListener {

    private List<Strategy> strategyList;
    private Painter painter;

    private class StrategyException extends Exception {
        public StrategyException() {
            super("Ran out of bars");
        }

        @Override
        public String toString() {
            return this.getMessage();
        }
    }

    private static abstract class Strategy implements Constants, MessageListener {

        static boolean isForging;

        abstract boolean isValid();

        abstract void execute() throws StrategyException;

        public abstract String toString();

        static boolean playerIsNotBusy(long milliseconds) throws NullPointerException {
            Timer t = new Timer(milliseconds);
            while (t.isRunning()) {
                if (!Players.getLocal().isIdle()) {
                    return false;
                }
            }
            return true;
        }

        static boolean waitForWidget() {
            Timer t = new Timer((long) 5000);
            Widget w = Widgets.get(Bank.WIDGET);
            while (t.isRunning()) {
                if (w != null && w.isValid()) {
                    return true;
                }
            }
            return false;
        }

    }

    private class PlateSmith extends Strategy {

        @Override
        public boolean isValid() {
            return !isForging && Inventory.getCount(Constants.ADAMANT_BAR_ID) > 4;
        }

        @Override
        public void execute() {
            try {
                GameObject anvil = Objects.getNearest(Constants.ANVIL_ID);
                if (anvil != null) {
                    if (Strategy.playerIsNotBusy(Random.nextInt(1500, 2000))) {
                        if (!Widgets.get(300).getComponent(245).isVisible()) {
                            if (anvil.isOnScreen()) {
                                if (!Players.getLocal().isMoving()) {
                                    Inventory.useItem(Inventory.getItem(Constants.ADAMANT_BAR_ID), anvil);
                                }
                            } else {
                                if (!Players.getLocal().isMoving()) {
                                    Walking.getTileOnMap(anvil.getLocation()).randomize(1, 1).clickOnMap();
                                }
                            }
                        } else {
                            Mouse.scroll(false);
                            Widgets.get(300).getComponent(245).interact("Make All");
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException handled) {
                if (Menu.isOpen()) Menu.close();
            }
        }

        @Override
        public String toString() {
            return "Going to forge plates";
        }

        @Override
        public void messageReceived(MessageEvent me) {
            if (this.isValid()) {
                isForging = (me.getId() == 109 || me.getId() == 0) && (me.getMessage().contains("You make an adamant")
                        || me.getMessage().contains("Wasteless Smithing") || me.getMessage().contains("The Varrock armour allows you to smith"));
            }
        }
    }

    private class WaitingToFinishForging extends Strategy {

        @Override
        public boolean isValid() {
            return isForging && Inventory.getCount(Constants.ADAMANT_BAR_ID) > 5;
        }

        @Override
        public void execute() {
            if(Inventory.getCount(Constants.ADAMANT_BAR_ID) <= 5) {
                isForging = false;
            }
            if (Mouse.isPresent()) {
                Mouse.moveOffScreen();
            }
        }

        @Override
        public String toString() {
            return "Forging";
        }

        @Override
        public void messageReceived(MessageEvent me) {
            if (this.isValid()) {
                isForging = (me.getId() == 109 || me.getId() == 0) && me.getMessage().contains("You make an adamant") || me.getMessage().contains("Wasteless Smithing");
            }
        }
    }

    private class WalkingToBank extends Strategy {

        @Override
        public boolean isValid() {
            return Inventory.getCount(Constants.ADAMANT_BAR_ID) < 5;
        }

        @Override
        public void execute() {
            isForging = false;
            GameObject bankBooth = Objects.getNearest(Constants.BANK_BOOTH_ID);
            if (bankBooth != null) {
                if (!bankBooth.isOnScreen()) {
                    if (!Players.getLocal().isMoving()) {
                        Walking.getTileOnMap(bankBooth.getLocation()).randomize(1, 1).clickOnMap();
                    }
                } else {
                    if (!Players.getLocal().isMoving()) {
                        try {
                            bankBooth.interact("Use-quickly");
                        } catch (ArrayIndexOutOfBoundsException handled) {
                            if (Menu.isOpen()) Menu.close();
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Walking to bank";
        }

        @Override
        public void messageReceived(MessageEvent messageEvent) {

        }
    }

    private class Banking extends Strategy {

        @Override
        public boolean isValid() {
            return Bank.isOpen();
        }

        @Override
        public void execute() throws StrategyException {
            isForging = false;
            if (Strategy.waitForWidget()) {
                if (Inventory.contains(Constants.PLATE_ID)) {
                    Bank.depositAll();
                } else {
                    if (Bank.getCount(Constants.ADAMANT_BAR_ID) == 0) {
                        throw new StrategyException();
                    } else {
                        if (!Inventory.isFull() && !Inventory.contains(ADAMANT_BAR_ID)) {
                            Bank.withdraw(Constants.ADAMANT_BAR_ID, 0);
                        }
                        Bank.close();
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Banking";
        }

        @Override
        public void messageReceived(MessageEvent messageEvent) {

        }
    }


    @Override
    public boolean onStart() {
        painter = new Painter();
        strategyList = new ArrayList<Strategy>();
        strategyList.add(new Banking());
        strategyList.add(new PlateSmith());
        strategyList.add(new WaitingToFinishForging());
        strategyList.add(new WalkingToBank());
        return true;
    }

    @Override
    public void onFinish() {
        log.info("Runtime: " + Timer.format(painter.getRunTime()));
        log.info("XP gained: " + painter.xPGained());
        log.info("XP/hr: " + painter.xPPerHour());
    }

    @Override
    public int loop() {
        for (Strategy strategy : strategyList) {
            Task.sleep(Random.nextInt(400, 600));
            if (strategy.isValid()) {
                painter.getStatus(strategy.toString());
                try {
                    strategy.execute();
                } catch (StrategyException se) {
                    log.warning(se.toString());
                    return -1;
                }
            }
        }
        return 0;
    }

    @Override
    public void messageReceived(MessageEvent messageEvent) {
        for (Strategy strategy : strategyList) {
            strategy.messageReceived(messageEvent);
        }
    }

    @Override
    public void onRepaint(Graphics g) {
        painter.onRepaint(g);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        painter.mouseClicked(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

}
