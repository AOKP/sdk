/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.tools.lint.detector.api.LintConstants.IMAGE_VIEW;
import static com.android.tools.lint.detector.api.LintConstants.LINEAR_LAYOUT;
import static com.android.tools.lint.detector.api.LintConstants.TEXT_VIEW;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Checks whether the current node can be replaced by a TextView using compound
 * drawables.
 */
public class UseCompoundDrawableDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "UseCompoundDrawables", //$NON-NLS-1$
            "Checks whether the current node can be replaced by a TextView using compound drawables.",
            // TODO: OFFER MORE HELP!
            "A LinearLayout which contains an ImageView and a TextView can be more efficiently " +
            "handled as a compound drawable",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            UseCompoundDrawableDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link UseCompoundDrawableDetector} */
    public UseCompoundDrawableDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
                LINEAR_LAYOUT
        });
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int childCount = LintUtils.getChildCount(element);
        if (childCount == 2) {
            List<Element> children = LintUtils.getChildren(element);
            Element first = children.get(0);
            Element second = children.get(1);
            if ((first.getTagName().equals(IMAGE_VIEW) &&
                    second.getTagName().equals(TEXT_VIEW) &&
                    !first.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) ||
                ((second.getTagName().equals(IMAGE_VIEW) &&
                        first.getTagName().equals(TEXT_VIEW) &&
                        !second.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)))) {
                context.report(ISSUE, context.getLocation(element),
                        "This tag and its children can be replaced by one <TextView/> and " +
                                "a compound drawable", null);
            }
        }
    }
}