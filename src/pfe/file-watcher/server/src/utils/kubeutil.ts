/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
"use strict";

import { promisify } from "util";
import { exec } from "child_process";
import { Operation } from "../projects/operation";
const execAsync = promisify(exec);
import * as path from "path";
import * as logger from "./logger";
import { ContainerStates } from "../projects/constants";
import * as processManager from "./processManager";
import { ProcessResult } from "./processManager";
import { ProjectInfo } from "../projects/Project";

const Client = require("kubernetes-client").Client; // tslint:disable-line:no-require-imports
const config = require("kubernetes-client").config; // tslint:disable-line:no-require-imports
let k8sClient: any = undefined;

if (process.env.IN_K8) {
    k8sClient = new Client({ config: config.getInCluster(), version: "1.9"});
}

const KUBE_NAMESPACE = process.env.KUBE_NAMESPACE || "default";

export interface PodInfo {
    podName: string;
    ip: string;
    serviceName: string;
    exposedPort: string;
    internalPort: string;
    podPorts: string[];
  }

  /**
   * @function
   * @description Get kube pod info.
   *
   * @param projectInfo <Required | ProjectInfo> - The metadata information for a project.
   * @param operation <Required | Operation> - The operation to get kube pod info.
   *
   * @returns Promise<PodInfo>
   */
export async function getApplicationContainerInfo(projectInfo: ProjectInfo, operation: Operation): Promise<PodInfo> {
    const projectID = projectInfo.projectID;
    const info: PodInfo = {
        podName: "",
        ip: "",
        serviceName: "",
        exposedPort: "",
        internalPort: "",
        podPorts: []
      };
    const projectLocation = operation.projectInfo.location;
    if (projectLocation.endsWith("/")) {
        projectLocation.slice(0, -1);
    }

    const releaseName = operation.containerName;
    const releaseLabel = "release=" + releaseName;
    const projectName = path.basename(projectLocation);

    // Before deploying the application, we added a release label to the deployment, pod, and service,
    // Use that to get the application's pod and service names.
    // If the application is stuck in the starting state with missing ports, it's likely a step below here failed.

    // Get the pod name for project
    try {
        let podName;
        const resp = await k8sClient.api.v1.namespaces(KUBE_NAMESPACE).pods.get({ qs: { labelSelector: releaseLabel } });
        // We are getting the list of pods by the release label
        for ( let i = 0 ; i < resp.body.items.length ; i++ ) {
            if (resp.body.items[i].status && resp.body.items[i].status.phase) {
                // For a terminating pod, the metadata.deletionTimestamp will be set
                if (resp.body.items[i].status.phase !== "Running") {
                    logger.logProjectInfo("Application pod is not running, the status is: " + resp.body.items[i].status.phase, projectID, projectName);
                    continue;
                } else if (resp.body.items[i].metadata.deletionTimestamp == undefined) {
                    podName = resp.body.items[i].metadata.name;
                }
            }
        }

        info.podName = podName;
        if (podName) {
            logger.logProjectInfo("Found the pod while looking up service information for project.", projectID, projectName);
            logger.logProjectInfo("Pod name: " + podName, projectID, projectName);
        } else {
            logger.logProjectInfo("Pod name was not found while looking up service information for project.", projectID, projectName);
        }
    } catch (err) {
        logger.logProjectError("Failed to get the pod name for: " + path.basename(projectLocation), projectID, projectName);
        logger.logProjectError(err, projectID, projectName);
    }

    /*
     *  IMPORTANT: If the pod name is undefined that means the helm install failed so there isn't actually a release so we should return
     *  at this point since it's possible that another release can have a deployment with the same name and we'd end up sending the port/ip
     *  information for the wrong deployment and marking the application as running.
     */
    if (info.podName === undefined) {
        return info;
    }

    // Get the internal and exposed ports
    try {
        const internalPorts: Array<string> = [];
        const exposedPorts: Array<string> = [];
        const appPorts = projectInfo.appPorts;
        const resp = await k8sClient.api.v1.namespaces(KUBE_NAMESPACE).services.get({ qs: { labelSelector: releaseLabel } });

        // iterate through the available ports and store both the internal and exposed ports
        for ( let i = 0 ; i < resp.body.items.length ; i++ ) {
            info.serviceName = resp.body.items[i].metadata.name;
            for (let j = 0; j < resp.body.items[i].spec.ports.length; j++) {
                internalPorts.push(String(resp.body.items[i].spec.ports[j].targetPort));
                exposedPorts.push(String(resp.body.items[i].spec.ports[j].nodePort));
            }
        }

        info.podPorts = internalPorts;

        let appPortMatched = false;

        // iterate through the list of ports recieved to match with the internal ports
        for (let i = 0; i < appPorts.length; i++) {
            const port = appPorts[i];

            // check if the appPort recieved matches any of the available ports
            if (internalPorts.includes(port)) {

              // set the internal port to be the matched appPort
              info.internalPort = port;

              const indexOfPort = internalPorts.indexOf(port);
              // set the exposed port that matches the matched internal port
              info.exposedPort = exposedPorts[indexOfPort];

              // set the app port to be matched only if the exposed port has been set
              appPortMatched = info.exposedPort ? true : false;

              // break out of the loop if the port has been assigned
              break;
            }
        }

        // if the app ports didn't match, assign them to the first available port
        if (!appPortMatched) {
            info.internalPort = internalPorts[0];
            info.exposedPort = exposedPorts[0];
        }

        // For Che on K8s, we are now using the service name for the application ping hostname
        // Check projectUtil.isApplicationUp() for relevant logic
        // log the service name/hostname
        logger.logProjectInfo("IP address info: " + info.serviceName, projectID, projectName);

        // log internalPorts and exposedPorts
        logger.logProjectInfo("Internal port: " + info.internalPort, projectInfo.projectID, projectInfo.projectName);
        logger.logProjectInfo("Exposed port: " + info.exposedPort, projectInfo.projectID, projectInfo.projectName);

        // log podPorts
        logger.logProjectInfo("All the exposed pod ports: " + JSON.stringify(info.podPorts), projectInfo.projectID, projectInfo.projectName);
    } catch (err) {
        logger.logProjectError("Failed to get the service internal/exposed port for project: " + projectName, projectID, projectName);
        logger.logProjectError(err, projectID, projectName);
    }

    return info;
}

/**
 * @function
 * @description Check to see if container is active.
 *
 * @param containerName <Required | String> - The kube container name.
 *
 * @returns Promise<any>
 */
export async function isContainerActive(containerName: string): Promise<any> {
    try {
        const releaseLabel = "release=" + containerName;
        let containerState = {state: ContainerStates.containerNotFound};
        // We are getting the list of pods by the release label
        const resp = await k8sClient.api.v1.namespaces(KUBE_NAMESPACE).pods.get({ qs: { labelSelector: releaseLabel } });
        const podInfo = resp.body;

        if (podInfo && podInfo.items && (Object.keys(podInfo.items).length > 0)) {
            for (const pod of podInfo.items) {
                if (pod.status && pod.status.phase) {
                    const podPhase =  pod.status.phase;
                    // Pod phases are Pending, Running, Succeeded (terminated in success),
                    // Failed, Unknown
                    if (podPhase !== "Pending" && podPhase !== "Running") {
                        continue;
                    }
                }
                if (pod.status && pod.status.containerStatuses &&
                    Object.keys(pod.status.containerStatuses).length > 0) {
                    for (const containerStatus of pod.status.containerStatuses) {
                        // Container state entries are running, terminated, waiting
                        // Container waiting reasons are: ContainerCreating, CrashLoopBackOff, ErrImagePull, ImagePullBackOff
                        if (containerStatus.state && containerStatus.state.running) {
                            containerState = {state: ContainerStates.containerActive};
                        } else if (containerStatus.state && containerStatus.state.waiting &&
                            containerStatus.state.waiting.reason &&
                            containerStatus.state.waiting.reason === "ContainerCreating") {
                            containerState = {state: ContainerStates.containerStarting};
                        } else {
                            // No active pods or containers
                            containerState = {state: ContainerStates.containerStopped};
                        }
                    }
                }
            }
        } else {
            containerState = {state: ContainerStates.containerNotFound};
        }

        return containerState;
    } catch (err) {
        const msg = "Error getting container status: " + err;
        logger.logError(msg);
        return {error: err};
    }
}

/**
 * @function
 * @description Remove the project's Helm release
 *
 * @param projectID <Required | String> - An alphanumeric identifier for a project.
 * @param deploymentName <Required | String> - The name of the release.
 *
 * @returns Promise<ProcessResult>
 */
export async function deleteHelmRelease(projectID: string, releaseName: string): Promise<ProcessResult> {
    const deleteRelease: string[] = ["delete", "--purge", releaseName];
    let response: ProcessResult;

    // Remove deployment
    try {
        logger.logProjectInfo("Removing Helm release", projectID);
        response = await runHelmCommand(projectID, deleteRelease);
    } catch (err) {
        logger.logProjectError("Error removing Helm release", projectID);
        logger.logProjectError(err, projectID);
        throw err;
    }

    return response;
}

/**
 * @function
 * @description Print helm status
 *
 * @param projectID <Required | String> - An alphanumeric identifier for a project.
 * @param releaseName <Required | String> - The name of the deployment.
 *
 * @returns Promise<void>
 */
export async function printHelmStatus(projectID: string, releaseName: string): Promise<void> {
    const helmStatus: string[] = ["status", releaseName];
    let response: ProcessResult;

    // Print the release
    try {
        logger.logProjectInfo("Printing Helm release", projectID);
        response = await runHelmCommand(projectID, helmStatus);
        logger.logProjectInfo(response.stdout, projectID);
    } catch (err) {
        logger.logProjectError("Error getting the Helm release", projectID);
        logger.logProjectError(err, projectID);
    }
}

/**
 * @function
 * @description Install helm chart.
 *
 * @param projectID <Required | String> - An alphanumeric identifier for a project.
 * @param deploymentName <Required | String> - The name of the deployment.
 * @param chartLocation <Required | String> - The location of the chart file.
 *
 * @returns Promise<ProcessResult>
 */
export async function installChart(projectID: string, deploymentName: string, chartLocation: string, deploymentRegistry: string): Promise<ProcessResult> {
    const microclimateReleaseName: string = process.env.MICROCLIMATE_RELEASE_NAME;
    const installDeployment: string[] = ["upgrade", "--install", deploymentName, "--recreate-pods", "--values=/file-watcher/scripts/override-values-icp.yaml", "--set", "image.repository=" + deploymentRegistry + "/" + deploymentName, "--set", "microclimate.release.name=" + microclimateReleaseName, chartLocation];
    let response: ProcessResult;

    // Install deployment
    try {
        logger.logProjectInfo("Installing helm chart", projectID);
        response = await runHelmCommand(projectID, installDeployment);
    } catch (err) {
        logger.logProjectError("Error installing helm chart", projectID);
        logger.logProjectError(err, projectID);
        throw err;
    }

    return response;
}

/**
 * @function
 * @description Run a helm command.
 *
 * @param projectID <Required | String> - An alphanumeric identifier for a project.
 * @param args <Required | String[]> - List of args to pass to the helm command.
 */
async function runHelmCommand(projectID: string, args: string[]): Promise<ProcessResult> {
    try {
        logger.logProjectInfo("Running helm command: helm " + args, projectID);
        return await processManager.spawnDetachedAsync(projectID, "helm", args, {});
    } catch (err) {
        throw err;
    }
}
