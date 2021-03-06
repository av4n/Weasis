package org.weasis.core.api.gui;

import java.util.Hashtable;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public interface PreferencesPageFactory {

    AbstractItemDialogPage createPreferencesPage(Hashtable<String, Object> properties);

}
