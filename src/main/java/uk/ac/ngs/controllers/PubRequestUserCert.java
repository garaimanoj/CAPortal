/*
 * Copyright (C) 2015 STFC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.ngs.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import uk.ac.ngs.common.MutableConfigParams;
import uk.ac.ngs.dao.JdbcRalistDao;
import uk.ac.ngs.domain.RalistRow;
import uk.ac.ngs.service.ProcessCsrNewService;
import uk.ac.ngs.service.ProcessCsrResult;
import uk.ac.ngs.validation.CsrRequestValidationConfigParams;

import javax.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the <code>/pub/requestUserCert</code> page.
 * The controller supports either POSTing of CSR attributes (so the CSR is
 * created on the server) or POSTing of a CSR generated client-side.
 *
 * @author David Meredith
 */
@Controller
@RequestMapping("/pub/requestUserCert")
public class PubRequestUserCert {

    private static final Log log = LogFactory.getLog(PubRequestUserCert.class);
    private JdbcRalistDao ralistDao;
    private CsrRequestValidationConfigParams csrRequestValidationConfigParams;
    public static final String RA_ARRAY_REQUESTSCOPE = "ralistArray";
    private MutableConfigParams mutableConfigParams;
    private ProcessCsrNewService processCsrNewService;

    @ModelAttribute
    public void populateModel(Model model/*, HttpSession session, ServletContext ctx*/) throws IOException {
        //log.debug("populateModel");
        // Populate the RA list pull down 
        List<RalistRow> rows = this.ralistDao.findAllByActive(true, null, null);
        List<String> raArray = new ArrayList<>(rows.size());

        for (RalistRow row : rows) {
            // BUG - have had trouble submitting RA values that contain whitespace, 
            // so have replaced whitespace in ra with underscore 
            raArray.add(row.getOu().trim() + " " + row.getL().trim());
        }
        model.addAttribute(RA_ARRAY_REQUESTSCOPE, raArray.toArray());
        model.addAttribute("countryOID", csrRequestValidationConfigParams.getCountryOID());
        model.addAttribute("orgNameOID", csrRequestValidationConfigParams.getOrgNameOID());

        model.addAttribute("createCsrOnClientOrServer", this.mutableConfigParams.getProperty("createCsrOnClientOrServer"));
        //if(true) throw new RuntimeException("test dave"); 
    }

    /**
     * Invoked initially to add the 'newUserCertFormBean' model attribute.
     */


    /**
     * Handle GETs to '/pub/requestUserCert' (i.e. the controllers base url).
     */
    @RequestMapping(method = RequestMethod.GET)
    public String handleBaseUrlGetRequest() {
        return "redirect:/pub/requestUserCert/submitNewUserCertRequest";
    }

    /**
     * Handle GETs to '/pub/requestUserCert/submitNewUserCertRequest'.
     */
    @RequestMapping(value = "submitNewUserCertRequest", method = RequestMethod.GET)
    public String handleSubmitNewUserCertRequest() {
        return "pub/requestUserCert/submitNewUserCertRequest";
    }


    /**
     * Accepts POSTed CSR attributes needed to build a new PKCS#10 on the server,
     * performs validation and inserts a new row in the <tt>request</tt> table if valid.
     * Using this method requires that the CSR and the public/private keys
     * are created server-side rather than by the client.
     * <p/>
     * If the request succeeds, 'SUCCESS' is returned appended with the
     * PKCS#10 PEM string and the encrypted PKCS#8 private key PEM string.
     * If the request fails, 'FAIL' is returned appended with an error message.
     * Sample return String on success:
     * <pre>
     * SUCCESS: CSR submitted ok [1234]
     *
     * -----BEGIN CERTIFICATE REQUEST-----
     *  MIIC1zC.....blah......
     * -----END CERTIFICATE REQUEST-----
     *
     * -----BEGIN ENCRYPTED PRIVATE KEY-----
     * MIIE....blash......
     * -----END ENCRYPTED PRIVATE KEY-----
     * </pre>
     *
     * @param cn                        Common Name
     * @param ra                        RA
     * @param email
     * @param pw                        Used to encrypt the private key
     * @param pin                       Used to identify that the request belongs to the submitter
     * @param request
     * @param model
     * @return Either 'SUCCESS' or 'FAIL' which always comes at the start of the string
     * and append either the CSR/keys on success or an error message on fail.
     * @throws IOException
     */
    @RequestMapping(value = "postCsrAttributes", method = RequestMethod.POST)
    public @ResponseBody
    String submitNewCertRequestCreateCSR_KeysOnServer(
            @RequestParam String cn,
            @RequestParam String ra,
            @RequestParam String email,
            @RequestParam String pw,
            @RequestParam String pin,
            HttpServletRequest request,
            //@Valid @ModelAttribute("newUserCertFormBean") NewUserCertFormBean newUserCertFormBean,
            //BindingResult result,
            /*RedirectAttributes redirectAttrs, */ Model model)
            throws IOException {
        //String cn = newUserCertFormBean.getName();

        ProcessCsrNewService.CsrAttributes csrAttributes = new ProcessCsrNewService.CsrAttributes(pw, cn, ra);
        ProcessCsrResult result = this.processCsrNewService.processNewUserCSR_CreateOnServer(
                csrAttributes, email, pin);
        return getReturnString(result, false);
    }


    /**
     * Accepts a POSTed PKCS#10 CSR request that is provided by the client,
     * performs validation and inserts a new row in the <tt>request</tt> table if valid.
     * Using this method requires that the CSR and the public/private keys
     * are created by the client rather than by the server.
     *
     * @param pin
     * @param email
     * @param csr
     * @param request
     * @return Either 'SUCCESS' or 'FAIL' which always comes at the start of the string
     * @throws IOException
     */
    @RequestMapping(value = "postCsr", method = RequestMethod.POST)
    public @ResponseBody
    String submitNewCertRequestCreateCSR_KeysOnClient(
            @RequestParam String pin,
            @RequestParam String email,
            @RequestParam String csr,
            HttpServletRequest request/*, ServletContext ctx*/)
            throws IOException {

        ProcessCsrResult result = this.processCsrNewService.processNewUserCSR_Provided(
                csr, email, pin);
        return getReturnString(result, true);
    }


    private String getReturnString(ProcessCsrResult result, boolean csrProvided) {
        String returnResult = "";
        if (result.isSuccess()) {
            returnResult += "SUCCESS: CSR submitted ok [" + result.getReq_key() + "]";
            if (!csrProvided) {
                returnResult += "\n" + result.getPkcs8PrivateKey() + "\n" + result.getCsrWrapper().getCsrPemString();
            }
        } else {
            returnResult = "FAIL: " + HtmlUtils.htmlEscapeHex(result.getErrors().getAllErrors().get(0).getDefaultMessage());
        }
        return returnResult;
    }

    @Inject
    public void setRalistDao(JdbcRalistDao ralistDao) {
        this.ralistDao = ralistDao;
    }

    @Inject
    public void setCsrRequestValidationConfigParams(CsrRequestValidationConfigParams csrRequestValidationConfigParams) {
        this.csrRequestValidationConfigParams = csrRequestValidationConfigParams;
    }

    @Inject
    public void setMutableConfigParams(MutableConfigParams mutableConfigParams) {
        this.mutableConfigParams = mutableConfigParams;
    }


    /**
     * @param processCsrNewService the ProcessCsrNewService to set
     */
    @Inject
    public void setProcessCsrNewService(ProcessCsrNewService processCsrNewService) {
        this.processCsrNewService = processCsrNewService;
    }
}
