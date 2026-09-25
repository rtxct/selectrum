package com.selectrum.application.component;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.selectrum.domain.model.ScheduleEntry;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ThemeFinder {

    private static ThemeFinder INSTANCE;

    public static  ThemeFinder instance() {
        if (INSTANCE == null) {
            INSTANCE = new ThemeFinder();
        }

        return INSTANCE;
    }

    public UIThemeLookAndFeelInfo findThemeByName(LafManager lafManager, String name) {
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

    public EditorColorsScheme findEditorSchemeByName(String name) {
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


    public ScheduleEntry findActiveEntry(List<ScheduleEntry> entries, LocalTime now) {
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