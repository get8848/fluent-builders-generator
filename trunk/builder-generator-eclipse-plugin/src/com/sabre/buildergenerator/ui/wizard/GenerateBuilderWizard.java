/*
* Copyright (c) 2009 by Sabre Holdings Corp.
* 3150 Sabre Drive, Southlake, TX 76092 USA
* All rights reserved.
*
* This software is the confidential and proprietary information
* of Sabre Holdings Corporation ("Confidential Information").
* You shall not disclose such Confidential Information and shall
* use it only in accordance with the terms of the license agreement
* you entered into with Sabre Holdings Corporation.
*/

package com.sabre.buildergenerator.ui.wizard;

import com.sabre.buildergenerator.sourcegenerator.BuilderGenerationProperties;

import org.eclipse.jface.wizard.Wizard;


/**
 * Title: GenerateBuilderWizard.java<br>
 * Description: <br>
 * Created: Dec 9, 2009<br>
 * Copyright: Copyright (c) 2007<br>
 * Company: Sabre Holdings Corporation
 * @author Jakub Janczak sg0209399
 * @version $Rev$: , $Date$: , $Author$:
 */

public class GenerateBuilderWizard extends Wizard {
    private final GenerateBuilderWizardPage generateBuilderWizardPage;

    public GenerateBuilderWizard(BuilderGenerationProperties properties) {
        generateBuilderWizardPage = new GenerateBuilderWizardPage("mainPage", properties);

        this.addPage(generateBuilderWizardPage);
        this.setWindowTitle("Generate builder");
    }

    /**
     * @return
     */
    public BuilderGenerationProperties getBuilderGenerationProperties() {
        return generateBuilderWizardPage.getBuilderGenerationProperties();
    }

    /**
     * (non-Javadoc)
     * @see org.eclipse.jface.wizard.Wizard#performFinish()
     */
    @Override public boolean performFinish() {
        return generateBuilderWizardPage.isValid();
    }
}
