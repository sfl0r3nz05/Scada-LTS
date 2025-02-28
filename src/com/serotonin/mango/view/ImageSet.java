/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.view;

import java.util.ArrayList;
import java.util.List;

public class ImageSet extends ViewGraphic {
    private final List<String> imageFilenames = new ArrayList<>();

    ImageSet(String id, String name, String[] imageFilenames, int width, int height, int textX, int textY) {
        super(id, name, width, height, textX, textY);
        for (String filename : imageFilenames)
            this.imageFilenames.add(filename);
    }

    public static ImageSet unavailable(String id) {
        return new ImageSet(id, "", new String[]{}, WIDTH_HEIGHT_DEFAULT, WIDTH_HEIGHT_DEFAULT, TEXT_X_Y_DEFAULT, TEXT_X_Y_DEFAULT);
    }

    public static ImageSet newInstance(String id, String name, String[] imageFilenames,
                                       int width, int height, int textX, int textY) {
        return new ImageSet(id, name, imageFilenames, width, height, textX, textY);
    }

    @Override
    public boolean isAvailable() {
        return !imageFilenames.isEmpty();
    }

    @Override
    public boolean isImageSet() {
        return true;
    }

    public int getImageCount() {
        return imageFilenames.size();
    }

    public String getImageFilename(int index) {
        return isAvailable() ? imageFilenames.get(index) : getUnavailableImg();
    }

    public List<String> getImageFilenames() {
        return imageFilenames;
    }
}
