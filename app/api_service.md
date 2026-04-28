Optimizing tool selection...

Read [](file:///f%3A/Disk%28D%29/Nayem/Android%20App/facerecognition/lib/submitted_reports_page.dart#1-1), lines 1 to 200

Read [](file:///f%3A/Disk%28D%29/Nayem/Android%20App/facerecognition/lib/submitted_reports_page.dart#200-200), lines 200 to 400

Searched for regex `getSpecialReports|getWorkplaceReports|getPersonalReports|getAcademicReports` (`**/lib/api_service.dart`), 4 results

Read [](file:///f%3A/Disk%28D%29/Nayem/Android%20App/facerecognition/lib/api_service.dart#499-499), lines 499 to 530

Here are the APIs used by the Submitted Reports Page:

## 1. **Get Special Reports**
- **Endpoint**: `/SpecialReport/specialReport/list/{userAutoID}`
- **Method**: GET
- **No request body needed**

## 2. **Get Workplace Reports**
- **Endpoint**: `/WorkingPlaceReport/list/{userAutoID}`
- **Method**: GET
- **No request body needed**

## 3. **Get Personal Reports**
- **Endpoint**: `/UserPersonalAndFamilyInfo/list/{userAutoID}`
- **Method**: GET
- **No request body needed**

## 4. **Get Academic Reports**
- **Endpoint**: `/Education/list/{userAutoID}`
- **Method**: GET
- **No request body needed**

## 5. **Get Incident Reports**
- **Endpoint**: `/IncidentReport/list/{userAutoID}`
- **Method**: GET
- **No request body needed**


**Note**: 
- `{userAutoID}` is retrieved from SharedPreferences with key `'userAutoID'`
- All requests include `Authorization: Bearer {token}` header (token from SharedPreferences)
- All are GET requests that fetch submitted reports by agent/user ID
- Response contains `responseObj` or `items` field with the list of reports