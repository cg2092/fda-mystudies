/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.beans;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsentHistory {
  private String id;

  private String consentVersion;

  private String consentedDate;

  private String consentDocumentPath;

  private String dataSharingPermissions;

  private String createTimeStamp;
}
