# Community Visitor Management

A second-year Java project based on **Gated Community Visitor Pre-Approval with OTP**.
Residents register visitors; security validates a one-time pass and records entry.

The Arrival interface includes a responsive dashboard, community statistics, a shareable
visitor pass preview, and animated feedback. Its CSS animations respect the device's
reduced-motion preference. It uses plain HTML, CSS and JavaScript with no frontend build step.

## Requirements and running

- Java 21
- Maven (or the included Maven wrapper)
- A browser and MySQL for normal use; an H2 demo profile is also available

From this folder in PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8080**. Stop the server with Ctrl+C.
If Maven is installed, `mvn spring-boot:run` also works.
The first build needs internet access to download dependencies.

MySQL is the default database. This computer's credentials are read from the
Git-ignored `local-config/mysql.properties`, or can be provided through DB_USERNAME/DB_PASSWORD.
For a standalone demo without MySQL, run with `--spring.profiles.active=demo`.
That profile saves an H2 database in `data/`. Existing H2 records are not automatically moved to MySQL.
Tests use separate in-memory databases and do not change demo data.
Spring Boot 3.5.6 and Java 21 are used, with Spring Web, Spring Data JPA and Validation.

## Demonstration

1. Add flat **A-101**.
2. Add a resident and select that flat.
3. Select the resident, enter visitor details, and choose arrival and expiry times.
4. For immediate entry, set arrival to a minute ago and expiry to an hour from now.
5. Create the pass. Share both the **approval ID** and **six-digit OTP** with the visitor.
6. In Security gate, enter those values. The visit becomes ENTERED and entry time is recorded.
7. Submit the same values again: entry is rejected.
8. Create another visit and revoke it. Its OTP must fail.
9. Create a short visit window and wait until it expires. Entry must fail even before the scheduled cleanup runs.

The visit window includes the arrival time and excludes the expiry time.
All times are local to the machine running Java; use the same timezone in the browser.
A visit may start in the past if its expiry is still in the future, allowing visitors arriving now.

## MySQL configuration

On this computer, credentials are stored in the Git-ignored file
`local-config/mysql.json`. To run with those saved settings:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-mysql.ps1
```

Stop any existing application on port 8080 first. The local configuration contains
a password; do not share it with your project submission. The normal Java/Maven startup also
reads `local-config/mysql.properties`; it is ignored by Git as well.
MySQL starts with separate data from the default H2 database.

Start MySQL and provide credentials in PowerShell:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:DB_URL="jdbc:mysql://localhost:3306/visitor_management?createDatabaseIfNotExist=true"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

The database user must be allowed to create the database, or create `visitor_management` beforehand.
Hibernate creates/updates the tables. MySQL and H2 have separate data.

## SMS setup (Twilio)

Real SMS requires your own Twilio account, credentials and an SMS-capable sender.
SMS is disabled by default. The project does not include a free SMS service.
Set these environment variables in the same PowerShell window used to start Java:

```powershell
$env:SMS_ENABLED="true"
$env:TWILIO_ACCOUNT_SID="your_account_sid"
$env:TWILIO_AUTH_TOKEN="your_auth_token"
$env:TWILIO_FROM_NUMBER="+your_twilio_sender_number"
java -jar target/visitor-management-0.0.1-SNAPSHOT.jar
```

Replace the placeholders locally; do not put credentials in source control or send them in chat.
Restart the application after changing the environment, then refresh the browser.
The visitor form will show that SMS is enabled.

1. Register a flat and resident, then enter the visitor's mobile number.
2. Create the visitor pass. The browser saves the approval first, then requests SMS submission.
3. The SMS contains the existing approval ID, OTP and visit window.
4. Use **Send / resend OTP by SMS** in the pass preview, or **Send OTP** in visit history, to retry.

Ten-digit Indian mobile numbers starting with 6–9 are prefixed with +91.
Other numbers must include a +country code. SMS is sent to the visitor number saved on
the approval, never to a separate number supplied to the send endpoint.

The message on screen distinguishes SUBMITTED (provider accepted it), FAILED
(provider rejected it), and UNKNOWN (a connection problem). SUBMITTED does not mean
the phone has received the SMS. Check Twilio's message logs for final delivery status.
An unknown result might still arrive, so check those logs before resending.
Requests for the same pass must be at least 60 seconds apart.
Failed SMS does not delete the approval or change its OTP.
Entered, revoked and expired passes cannot be sent.

Twilio trial accounts require verified recipients. Sender availability, account restrictions
and destination-country permissions also affect delivery:
[Twilio Messages API](https://www.twilio.com/docs/messaging/api/message-resource).
Tests mock the provider; they do not send billable SMS.

Additional endpoints:

| Method | URL | Purpose |
| --- | --- | --- |
| GET | /api/sms/status | Whether the server has enabled/configured SMS |
| POST | /api/residents/{residentId}/approvals/{approvalId}/sms | Submit the saved OTP to the saved visitor number |

SMS submission returns a JSON `status` and `message`. Configuration errors return 503,
invalid phone numbers 400, missing approvals 404, unavailable passes 409 and rapid retries 429.

## Visitor API

JSON request and response bodies are used. POST creation returns 201; successful reads,
revocations and entries return 200. Invalid input returns 400, missing records 404,
and invalid state changes or duplicates 409. Errors contain a readable `message`.

| Method | URL | Purpose |
| --- | --- | --- |
| GET / POST | /api/flats | List / create flats |
| GET / POST | /api/residents | List / create residents |
| PUT | /api/flats/{id} | Update a flat number and address |
| PUT | /api/residents/{id} | Update resident name, phone and assigned flat |
| GET | /api/residents/{residentId}/approvals | Resident visit history |
| POST | /api/residents/{residentId}/approvals | Approve visitor and generate OTP |
| PUT | /api/residents/{residentId}/approvals/{approvalId}/revoke | Revoke a pending visit |
| POST | /api/security/entries | Validate OTP and record entry |

Create a flat: `{"number":"A-101"}`

To edit details in the browser, open **Community setup**, select an existing record
under **Add or edit a flat** or **Add or edit a resident**, change the fields, and
click **Save flat and address** or **Save resident changes**. Changes are saved when
you press Save, and remain after refreshing the page or restarting the server.
Editing a shared flat address changes it for all residents assigned to that flat.

Update a flat: `PUT /api/flats/1` with
`{"number":"A-102","address":"12 College Road, Coimbatore"}`.
Updating uses the same database ID, so it does not create duplicate records.

Create a resident: `{"name":"Anu","phone":"9876543210","flatId":1}`

Approve a visitor (replace dates with a current visit window):

```json
{
  "visitorName": "Ravi",
  "visitorPhone": "9876543211",
  "purpose": "Family visit",
  "expectedAt": "2026-09-28T10:00:00",
  "expiresAt": "2026-09-28T12:00:00"
}
```

Record entry: `{"approvalId":1,"otp":"012345"}`

An OTP is a string, so leading zeroes must be preserved.
The approval ID identifies the pass; the code is not globally unique.

Import `docs/Visitor-Management.postman_collection.json` into Postman.
Run requests in order: the collection stores IDs and OTPs automatically and creates
a visit window around the current time. Run the edge-case requests after the main flow.
Use the Collection Runner for the full scenario.

## Design and explanation

```text
Browser / Postman
      |
VisitorController       HTTP endpoints and input validation
      |
VisitorService          Business rules and transactions
      |
Repository interfaces   Spring Data JPA database operations
      |
H2 / MySQL
```

| Entity | Main fields | Relationship |
| --- | --- | --- |
| Flat | id, number (unique), address | One flat can have many residents |
| Resident | id, name, phone, flat | Many residents belong to one flat |
| VisitorPreApproval | id, resident, visitorName, visitorPhone, purpose, expectedAt, expiresAt, status, entryTime | Many approvals belong to one resident |
| OTPPass | id, approval, code, used | Exactly one pass is created for each approval |

The child entity holds the foreign key, using `@ManyToOne` or `@OneToOne`.
Repository interfaces extend `JpaRepository`, which provides save/find operations.
Entities use ordinary getters and setters so their code is easy to follow.
DTO records hold request/response data, separate from editable database entities.

Possible transitions:

```text
PENDING -> ENTERED  (correct OTP during visit window)
PENDING -> REVOKED  (resident cancels before entry and expiry)
PENDING -> EXPIRED  (expiry time reached)
```

The service checks rules before changing records.
`@Transactional` saves the pass and approval together, or rolls both back on failure.
A database row lock serializes entry/revocation requests for the same approval.
`@Scheduled` marks unused expired approvals every minute. Entry validation and displayed
status check the actual time independently, so a pass cannot slip through between runs.
The global exception handler turns validation and business errors into readable JSON.

## Tests and packaging

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/visitor-management-0.0.1-SNAPSHOT.jar
```

Automated integration tests cover successful entry, reuse, wrong OTP, expiry,
revocation, future visits, invalid visit windows, cross-resident revocation,
duplicate flats, concurrent validation, HTTP input validation and serving the UI.

## Scope

This is a classroom demonstration with a shared resident selector and security view.
There is no login or role authentication; resident IDs are selected by the caller.
OTP delivery supports optional Twilio SMS; manual copy/share remains available. Email is not integrated.
The demo stores OTPs in the database and shows pending passes in resident history.
A real deployment would need authenticated roles, OTP protection and attempt limits.
Pagination, caching, and a separate audit log are optional extensions from the assignment
and are not implemented. Entry timestamps and visit statuses are retained.
