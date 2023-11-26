/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.audio.playbackfx;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

/**
 * Minimal audio playback view
 */
public class AudioPlaybackChannelViewMinimal extends AudioPlaybackChannelView
{
    /**
     * Constructs an instance
     * @param controller for audio playback that backs this view.
     */
    public AudioPlaybackChannelViewMinimal(AudioPlaybackChannelController controller)
    {
        super(controller);
        init();
    }

    /**
     * Reset width
     */
    public void resetWidth()
    {
        //No-op
    }

    private void init()
    {
        setSpacing(3);
        setMaxWidth(Double.MAX_VALUE);
        setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, null, null)));
        setPadding(new Insets(0, 3, 0, 3));
        GridPane gridPane = new GridPane();
        gridPane.setHgap(3);

        int row = 0;

        GridPane.setHalignment(getMuteLabel(), HPos.LEFT);
        GridPane.setHgrow(getMuteLabel(), Priority.NEVER);
        gridPane.add(getMuteLabel(), 0, row);

        GridPane.setHalignment(getLockLabel(), HPos.LEFT);
        GridPane.setHgrow(getLockLabel(), Priority.NEVER);
        gridPane.add(getLockLabel(), 1, row);

        Label toLabel = new Label("TO:");
        toLabel.setDisable(true);
        GridPane.setHalignment(toLabel, HPos.RIGHT);
        GridPane.setHgrow(toLabel, Priority.NEVER);
        gridPane.add(toLabel, 2, row);

        GridPane.setHalignment(getToLabel(), HPos.RIGHT);
        GridPane.setHgrow(getToLabel(), Priority.NEVER);
        gridPane.add(getToLabel(), 3, row);

        GridPane.setHalignment(getToAliasLabel(), HPos.LEFT);
        GridPane.setHgrow(getToAliasLabel(), Priority.ALWAYS);
        getToAliasLabel().setMaxWidth(Double.MAX_VALUE);
        gridPane.add(getToAliasLabel(), 4, row);

        GridPane.setHalignment(getPlaybackMode(), HPos.RIGHT);
        GridPane.setHgrow(getPlaybackMode(), Priority.SOMETIMES);
        gridPane.add(getPlaybackMode(), 4, row);

        getChildren().add(gridPane);
    }
}
