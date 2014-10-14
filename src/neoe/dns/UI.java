package neoe.dns;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.Connection;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 *
 * @author neoe
 */
class UI {

    static TrayIcon trayIcon;

    static Image createImage(String path, String description) throws IOException {
        return (new ImageIcon(U.readBytes(U.getIns(path)), description)).getImage();

    }

    static void addUI() throws IOException {
        if (!SystemTray.isSupported()) {
            Log.app.log("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();

        trayIcon = new TrayIcon(createImage("icon.png", "tray icon"));
        trayIcon.setImageAutoSize(true);
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        final MenuItem saveCache = new MenuItem("Save Cache");
        Menu menuClear = new Menu("Clear Cache");
        final MenuItem clearExact = new MenuItem("Clear exact name");
        final MenuItem clearWild = new MenuItem("Clear wildcard name");
        final MenuItem clearAll = new MenuItem("Clear all");
        final MenuItem exitItem = new MenuItem("Exit");

        //Add components to pop-up menu
        //popup.add(saveCache);
        //popup.addSeparator();
        popup.add(menuClear);
        menuClear.add(clearExact);
        menuClear.add(clearWild);
        menuClear.add(clearAll);
        popup.addSeparator();
        popup.add(exitItem);

        ActionListener handle = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Object src = e.getSource();
                    if (src == exitItem) {
                        //doSave(false);
                        System.exit(0);
                    } else if (src == saveCache) {
                        //doSave(true);
                    } else if (src == clearAll) {
                        doClearAll();
                    } else if (src == clearExact) {
                        doClearExact();
                    } else if (src == clearWild) {
                        doClearWild();
                    }
                } catch (Exception ex) {
                    U.showMsg("error:" + ex);
                }
                U.updateTooltip();

            }

            private void doClearAll() throws Exception {
                if (JOptionPane.showConfirmDialog(null, "Are you sure to clear ALL dns cache?") != JOptionPane.YES_OPTION) {
                    return;
                }
                int n = Cache.m.size();
                Cache.m.clear();
                Cache.updated.clear();
                U.inserted.clear();                
                U.showMsg("Cleared all " + n + " records.");
            }

            private void doSave(boolean showMsg) {
                try {
                    int n = Cache.save();
                    if (showMsg) {
                        U.showMsg("Saved " + n + " records.");
                    }
                } catch (Throwable ex) {
                    U.showMsg("Cannot save: " + ex);
                }
            }

            private void doClearExact() throws Exception {
                final String s = JOptionPane.showInputDialog("Input domain name");
                if (s != null) {
                    int n = Cache.clearExact(s);
                 
                    U.showMsg("Cleared " + n + " records.");
                }
            }

            private void doClearWild() throws Exception {
                final String s = JOptionPane.showInputDialog("Input domain name");
                if (s != null) {
                    int n = Cache.clearWild(s);
                  
                    U.showMsg("Cleared " + n + " records.");
                }
            }

        };
        saveCache.addActionListener(handle);
        clearExact.addActionListener(handle);
        clearWild.addActionListener(handle);
        clearAll.addActionListener(handle);
        exitItem.addActionListener(handle);

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
            U.updateTooltip();
        } catch (AWTException e) {
            U.sendException(e);
        }
    }

}
