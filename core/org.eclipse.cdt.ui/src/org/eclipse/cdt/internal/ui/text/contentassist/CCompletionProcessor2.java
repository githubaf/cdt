/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.cdt.internal.ui.text.contentassist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.dom.CDOM;
import org.eclipse.cdt.core.dom.IASTServiceProvider.UnsupportedDialectException;
import org.eclipse.cdt.core.dom.ast.ASTCompletionNode;
import org.eclipse.cdt.core.model.IWorkingCopy;
import org.eclipse.cdt.internal.ui.text.CParameterListValidator;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.text.contentassist.ICompletionContributor;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.ui.IEditorPart;

/**
 * @author Doug Schaefer
 */
public class CCompletionProcessor2 implements IContentAssistProcessor {

	private IEditorPart editor;
	private String errorMessage;
	
	
	public CCompletionProcessor2(IEditorPart editor) {
		this.editor = editor;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeCompletionProposals(org.eclipse.jface.text.ITextViewer, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(final ITextViewer viewer,
			int offset) {
		try {
			errorMessage = "No completions found";
			
			long startTime = System.currentTimeMillis();
			IWorkingCopy workingCopy = CUIPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(editor.getEditorInput());
			ASTCompletionNode completionNode = CDOM.getInstance().getCompletionNode(
				(IFile)workingCopy.getResource(),
				offset,
				CDOM.getInstance().getCodeReaderFactory(CDOM.PARSE_WORKING_COPY_WHENEVER_POSSIBLE));
			long stopTime = System.currentTimeMillis();

			List proposals = new ArrayList();
			
			IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(CUIPlugin.PLUGIN_ID, "completionContributors"); //$NON-NLS-1$
			if (point == null)
				return null;
			IExtension[] extensions = point.getExtensions();
			for (int i = 0; i < extensions.length; ++i) {
				IConfigurationElement[] elements = extensions[i].getConfigurationElements();
				for (int j = 0; j < elements.length; ++j) {
					IConfigurationElement element = elements[j];
					if (!"contributor".equals(element.getName())) //$NON-NLS-1$
						continue;
					Class contribClass = element.loadExtensionClass("class"); //$NON-NLS-1$
					Object contribObject = contribClass.newInstance();
					if (!(contribObject instanceof ICompletionContributor))
						continue;
					ICompletionContributor contributor = (ICompletionContributor)contribObject;
					contributor.contributeCompletionProposals(viewer, offset, completionNode, proposals);
				}
				int x = 5;
			}
			
			long propTime = System.currentTimeMillis();
			System.out.println("Completion Parse: " + (stopTime - startTime) + " + Proposals: " //$NON-NLS-1$ //$NON-NLS-2$
					+ (propTime - stopTime));
			System.out.flush();

			if (!proposals.isEmpty()) {
				errorMessage = null;
				return (ICompletionProposal[])proposals.toArray(new ICompletionProposal[proposals.size()]);
			}
			
		} catch (UnsupportedDialectException e) {
			errorMessage = "Unsupported Dialect Exception";
		} catch (Throwable e) {
			errorMessage = e.toString();
		}
		
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeContextInformation(org.eclipse.jface.text.ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer,
			int offset) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public char[] getCompletionProposalAutoActivationCharacters() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return errorMessage;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return new CParameterListValidator();
	}

}
