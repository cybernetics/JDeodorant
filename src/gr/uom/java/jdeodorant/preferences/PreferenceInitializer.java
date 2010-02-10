package gr.uom.java.jdeodorant.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import gr.uom.java.jdeodorant.refactoring.Activator;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.P_MINIMUM_SLICE_SIZE, 0);
		store.setDefault(PreferenceConstants.P_MAXIMUM_SLICE_SIZE, 0);
		store.setDefault(PreferenceConstants.P_MAXIMUM_DUPLICATION, 100);
		store.setDefault(PreferenceConstants.P_MAXIMUM_RATIO_OF_DUPLICATED_TO_EXTRACTED, 1.0);
	}

}