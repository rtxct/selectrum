package com.selectrum.application.component;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.selectrum.domain.model.ScheduleEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A utility component that helps find installed IDE themes and editor color schemes,
 * as well as determining the currently active schedule entry based on the current time.
 */
public class ThemeFinder {

    private static ThemeFinder INSTANCE;

    /**
     * Retrieves the singleton instance of the {@code ThemeFinder}.
     *
     * @return the instance of {@code ThemeFinder}.
     */
    public static @NotNull ThemeFinder instance() {
        if (INSTANCE == null) {
            INSTANCE = new ThemeFinder();
        }

        return INSTANCE;
    }

    /**
     * Finds a UI theme look and feel by its given name.
     *
     * @param lafManager the look and feel manager containing installed themes.
     * @param name       the name of the theme to search for.
     * @return the {@link UIThemeLookAndFeelInfo} matching the name, or {@code null} if not found.
     */
    public @Nullable UIThemeLookAndFeelInfo findThemeByName(@NotNull LafManager lafManager, @NotNull String name) {
        Iterator<UIThemeLookAndFeelInfo> themes =
                lafManager.getInstalledThemes().iterator();

        while (themes.hasNext()) {
            UIThemeLookAndFeelInfo theme = themes.next();

            if (theme.getName().equals(name)) {
                return theme;
            }
        }

        return null;
    }

    /**
     * Finds an editor color scheme by its given name. Checks exact name, exact display name,
     * and case-insensitive matching in that order.
     *
     * @param name the name of the editor scheme to search for.
     * @return the {@link EditorColorsScheme} matching the name, or {@code null} if not found.
     */
    public @Nullable EditorColorsScheme findEditorSchemeByName(@NotNull String name) {
        EditorColorsScheme[] allSchemes =
                EditorColorsManager.getInstance().getAllSchemes();

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equals(name)) return scheme;
        }

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getDisplayName().equals(name)) {
                return scheme;
            }
        }

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equalsIgnoreCase(name)
                    || scheme.getDisplayName().equals(name)) {
                return scheme;
            }
        }

        return null;
    }

    /**
     * Finds the active schedule entry for the given time.
     * The active entry is defined as the entry with the latest time that has already passed.
     * If no entry has passed today, the latest entry from the previous day (i.e., the last entry) is returned.
     *
     * @param entries a list of schedule entries to evaluate.
     * @param now     the current local time to check against.
     * @return the active {@link ScheduleEntry}.
     */
    public @Nullable ScheduleEntry findActiveEntry(@NotNull List<ScheduleEntry> entries, @NotNull LocalTime now) {
        List<ScheduleEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(ScheduleEntry::getParsedTime))
                .toList();

        ScheduleEntry active = null;
        for (ScheduleEntry entry : sorted) {
            if (!entry.getParsedTime().isAfter(now)) {
                active = entry;
            }
        }

        if (active == null) {
            active = sorted.getLast();
        }

        return active;
    }
}