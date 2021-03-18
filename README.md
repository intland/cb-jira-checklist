# Jira Checklist Support for codeBeamer
Extends the Jira connector to support importing and exporting the add-on field type [Checklist for Jira](https://marketplace.atlassian.com/apps/1211562/checklist-for-jira).
The extension is based on the [Add-on field type API](https://codebeamer.com/cb/wiki/13348438) added in codeBeamer 21.05 (Dorothy).

## Installation
1. Extract the archive found in releases to `<codeBeamer>/tomcat/webapps/`
2. Restart codeBeamer

## Usage
After installation the you should be able to map checklist fields in the configuration.

## Limitations
In the configuration UI it is possible to configure checklist fields for synchronization even so only importing is supported.
Attempting to export checklist fields will result in errors updating the field.
