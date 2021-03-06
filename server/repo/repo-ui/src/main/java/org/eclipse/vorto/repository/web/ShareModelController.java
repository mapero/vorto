/*******************************************************************************
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *   
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *   
 * Contributors:
 * Bosch Software Innovations GmbH - Please refer to git log
 *******************************************************************************/
package org.eclipse.vorto.repository.web;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.vorto.repository.internal.service.utils.BulkUploadHelper;
import org.eclipse.vorto.repository.model.ModelHandle;
import org.eclipse.vorto.repository.model.UploadModelResult;
import org.eclipse.vorto.repository.service.IModelRepository;
import org.eclipse.vorto.repository.web.model.ServerResponse;
import org.eclipse.vorto.repository.web.utils.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.collect.Lists;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 * @author Alexander Edelmann - Robert Bosch (SEA) Pte. Ltd.
 */
@Api(value="/share", description="Share your vorto models")
@RestController
@RequestMapping(value = "/rest/secure")
public class ShareModelController {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    
    private final String ERROR_MSG_FORMAT = "Models cannot be checked in because there {0,choice,1#is|1<are} {0} {0,choice,1#model|1<models} with {0,choice,1#error|1<errors}.";
	private final String SUCCESS_MSG_FORMAT = "{0,choice,1#The uploaded model is|1<All uploaded models are} valid and ready to check in.";
    
	@Autowired
	private IModelRepository modelRepository;
	
	private UploadModelResult uploadModelResult;
	
	@ApiOperation(value = "Upload and validate a single vorto model")
	@RequestMapping(method = RequestMethod.POST)
	public ResponseEntity<ServerResponse> uploadModel(@ApiParam(value = "The vorto model file to upload", required = true) @RequestParam("file") MultipartFile file) {
		LOGGER.info("uploadModel: [" + file.getOriginalFilename() + "]");
		try {
			FileHelper.copyFileToTempLocation(file);
			uploadModelResult = modelRepository.upload(file.getBytes(), file.getOriginalFilename());
			List<UploadModelResult> uploadModelResults = Lists.newArrayList();
			uploadModelResults.add(uploadModelResult);
			String message = "Uploaded model " + file.getOriginalFilename() + (uploadModelResult.isValid() ? " is valid to check in." : " has errors. Cannot check in.") ;
			return validResponse(new ServerResponse(message,true, uploadModelResults));
		} catch (IOException e) {
			LOGGER.error("Error upload model." + e.getStackTrace());
			ServerResponse errorResponse = new ServerResponse("Error during upload. Try again. " + e.getMessage(),
					false, null);
			FileHelper.deleteUploadedFile(file);
			return new ResponseEntity<ServerResponse>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiOperation(value = "Upload and validate multiple vorto models")
	@RequestMapping(value = "multiple", method = RequestMethod.POST)
	public ResponseEntity<ServerResponse> uploadMultipleModels(@ApiParam(value = "The vorto model files to upload", required = true) @RequestParam("file") MultipartFile file) {
		LOGGER.info("Bulk upload Models: [" + file.getOriginalFilename() + "]");
		try {
			FileHelper.copyFileToTempLocation(file);
			BulkUploadHelper bulkUploadService = new BulkUploadHelper(this.modelRepository);
			
			List<UploadModelResult> uploadModelResults = bulkUploadService.uploadMultiple(FileHelper.getDefaultExtractDirectory() + "/" + file.getOriginalFilename());
			LOGGER.info("Models Uploaded: [" + uploadModelResults.size() + "]");
			FileHelper.deleteUploadedFile(file);
			ServerResponse serverResponse = (uploadModelResults.size() == 0)
					? new ServerResponse("Uploaded file doesn't have any valid models.", false, uploadModelResults)
					: new ServerResponse(constructUserResponseMessage(uploadModelResults), true, uploadModelResults);
			return validResponse(serverResponse);
		} catch (Exception e) {
			LOGGER.error("Error bulk upload models.",e);
			FileHelper.deleteUploadedFile(file);
			return erroredResponse("Error during upload. Try again. " + e.getMessage());
		}
	}

	@ApiOperation(value = "Checkin an uploaded vorto model into the vorto repository")
	@RequestMapping(value = "/{handleId:.+}", method = RequestMethod.PUT)
	public ResponseEntity<ServerResponse> checkin(@ApiParam(value = "The file name of uploaded vorto model", required = true) final @PathVariable String handleId) {
		LOGGER.info("Check in Model " + handleId);
		try {
		modelRepository.checkin(handleId, SecurityContextHolder.getContext().getAuthentication().getName());
			ServerResponse successModelResponse = new ServerResponse("Model has been checkin successfully.",true, null);
			FileHelper.deleteUploadedFile(handleId);
			return validResponse(successModelResponse);
		} catch (Exception e) {
			LOGGER.error("Error checkin model. " + handleId +  e.getStackTrace());
			return erroredResponse("Error during checkin. Try again. " + e.getMessage());
		}
	}
	
	@ApiOperation(value = "Checkin multiple uploaded vorto models into the vorto repository")
	@RequestMapping(value = "/checkInMultiple", method = RequestMethod.PUT)
	public ResponseEntity<ServerResponse> checkInMultiple(@ApiParam(value = "The file name of uploaded vorto model", required=true) final @RequestBody ModelHandle[] modelHandles) {
		LOGGER.info("Bulk check in models.");
		try {
			for (ModelHandle handle : modelHandles) {
				modelRepository.checkin(handle.getHandleId(), SecurityContextHolder.getContext().getAuthentication().getName());
			}
			ServerResponse successModelResponse = new ServerResponse("All the models has been checked in Successfully.",true, null);
			FileHelper.deleteTempExtractFolder();
			return validResponse(successModelResponse);
		} catch (Exception e) {
			LOGGER.error("Error bulk checkin models." + e.getStackTrace());
			return erroredResponse("Error during checkin. Try again. " + e.getMessage());
		}
	}

	private ResponseEntity<ServerResponse> validResponse(ServerResponse successModelResponse) {
		return new ResponseEntity<ServerResponse>(successModelResponse, HttpStatus.OK);
	}

	private ResponseEntity<ServerResponse> erroredResponse(String errorMessage) {
		ServerResponse errorResponse = new ServerResponse("Error during checkin. Try again. " + errorMessage,
				false, null);
		return new ResponseEntity<ServerResponse>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	private String constructUserResponseMessage(List<UploadModelResult> uploadModelResults) {
		if(uploadModelResults.size() ==0)
			return "No valid models found. Nothing to checkin";
		long noOfInvalidModels = uploadModelResults.stream().filter(result -> !result.isValid()).count();
		
		return (noOfInvalidModels) > 0 ? resolveMessage(ERROR_MSG_FORMAT, noOfInvalidModels) : resolveMessage(SUCCESS_MSG_FORMAT, uploadModelResults.size()); 
	}

	private String resolveMessage(String messageFormat, long occurences) {
		return MessageFormat.format(messageFormat, occurences);
	}
}
