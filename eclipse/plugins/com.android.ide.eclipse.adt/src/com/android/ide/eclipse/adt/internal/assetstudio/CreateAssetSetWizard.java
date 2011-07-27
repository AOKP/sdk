/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.assetstudio;

import static com.android.ide.eclipse.adt.AdtConstants.DOT_PNG;
import static com.android.ide.eclipse.adt.AdtConstants.WS_RESOURCES;
import static com.android.ide.eclipse.adt.AdtConstants.WS_SEP;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper.IProjectFilter;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.NewXmlFileWizard;
import com.android.util.Pair;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class CreateAssetSetWizard extends Wizard implements INewWizard {
    private ChooseAssetTypePage chooseAssetPage;
    private ConfigureAssetSetPage configureAssetPage;
    private IProject mInitialProject;

    public CreateAssetSetWizard() {
        setWindowTitle("Create Asset Set");
    }

    @Override
    public void addPages() {
        chooseAssetPage = new ChooseAssetTypePage();
        chooseAssetPage.setProject(mInitialProject);
        configureAssetPage = new ConfigureAssetSetPage();

        addPage(chooseAssetPage);
        addPage(configureAssetPage);
    }

    @Override
    public boolean performFinish() {
        String name = chooseAssetPage.getOutputName();
        Map<String, BufferedImage> previews = configureAssetPage.generatePreviews();
        IProject project = getProject();

        // Write out the images into the project
        List<IFile> createdFiles = new ArrayList<IFile>();
        for (Map.Entry<String, BufferedImage> entry : previews.entrySet()) {
            String id = entry.getKey();

            IPath dest = new Path(WS_RESOURCES + WS_SEP + "drawable-" //$NON-NLS-1$
                    + id + WS_SEP + name + DOT_PNG);
            IFile file = project.getFile(dest);
            if (file.exists()) {
                // TODO: Warn that the file already exists and ask the user what to do?
                try {
                    file.delete(true, new NullProgressMonitor());
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }
            }

            NewXmlFileWizard.createWsParentDirectory(file.getParent());
            BufferedImage image = entry.getValue();

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "PNG", stream); //$NON-NLS-1$
                byte[] bytes = stream.toByteArray();
                InputStream is = new ByteArrayInputStream(bytes);
                file.create(is, true /*force*/, null /*progress*/);
                createdFiles.add(file);
            } catch (IOException e) {
                AdtPlugin.log(e, null);
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            try {
                file.getParent().refreshLocal(1, new NullProgressMonitor());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
        }

        // Attempt to select the newly created files in the Package Explorer
        IWorkbench workbench = AdtPlugin.getDefault().getWorkbench();
        IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
        IViewPart viewPart = page.findView(JavaUI.ID_PACKAGES);
        if (viewPart != null) {
            IWorkbenchPartSite site = viewPart.getSite();
            IJavaProject javaProject = null;
            try {
                javaProject = BaseProjectHelper.getJavaProject(project);
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }
            ISelectionProvider provider = site.getSelectionProvider();
            if (provider != null) {
                List<TreePath> pathList = new ArrayList<TreePath>();
                for (IFile file : createdFiles) {
                    // Create a TreePath for the given file,
                    // which should be the JavaProject, followed by the folders down to
                    // the final file.
                    List<Object> segments = new ArrayList<Object>();
                    segments.add(file);
                    IContainer folder = file.getParent();
                    segments.add(folder);
                    // res folder
                    folder = folder.getParent();
                    segments.add(folder);
                    // project
                    segments.add(javaProject);

                    Collections.reverse(segments);
                    TreePath path = new TreePath(segments.toArray());
                    pathList.add(path);
                }

                TreePath[] paths = pathList.toArray(new TreePath[pathList.size()]);
                provider.setSelection(new TreeSelection(paths));
            }
        }

        return true;
    }

    IProject getProject() {
        if (chooseAssetPage != null) {
            return chooseAssetPage.getProject();
        } else {
            return mInitialProject;
        }
    }

    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false);

        mInitialProject = guessProject(selection);
        if (chooseAssetPage != null) {
            chooseAssetPage.setProject(mInitialProject);
        }
    }

    private IProject guessProject(IStructuredSelection selection) {
        if (selection == null) {
            return null;
        }

        for (Object element : selection.toList()) {
            if (element instanceof IAdaptable) {
                IResource res = (IResource) ((IAdaptable) element).getAdapter(IResource.class);
                IProject project = res != null ? res.getProject() : null;

                // Is this an Android project?
                try {
                    if (project == null || !project.hasNature(AdtConstants.NATURE_DEFAULT)) {
                        continue;
                    }
                } catch (CoreException e) {
                    // checking the nature failed, ignore this resource
                    continue;
                }

                return project;
            } else if (element instanceof Pair<?, ?>) {
                // Pair of Project/String
                @SuppressWarnings("unchecked")
                Pair<IProject, String> pair = (Pair<IProject, String>) element;
                return pair.getFirst();
            }
        }

        // Try to figure out the project from the active editor
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                IEditorPart activeEditor = page.getActiveEditor();
                if (activeEditor instanceof AndroidXmlEditor) {
                    Object input = ((AndroidXmlEditor) activeEditor).getEditorInput();
                    if (input instanceof FileEditorInput) {
                        FileEditorInput fileInput = (FileEditorInput) input;
                        return fileInput.getFile().getProject();
                    }
                }
            }
        }

        IJavaProject[] projects = BaseProjectHelper.getAndroidProjects(new IProjectFilter() {
            public boolean accept(IProject project) {
                return project.isAccessible();
            }
        });

        if (projects != null && projects.length == 1) {
            return projects[0].getProject();
        }

        return null;
    }
}
