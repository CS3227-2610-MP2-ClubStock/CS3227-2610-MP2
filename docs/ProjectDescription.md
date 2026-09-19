# Project name: ClubStock

ClubStock is an equipment management application that is to be run on a single machine. There are two types of user roles present: Member and Exco

The Java Desktop application should be running on Java 25 (using JDK 25) and its correspondingly bundled JavaFX runtime, and should be running on Windows, macOS and Linux platforms. The application should be bundled into a Fat Jar file using the Gradle shadowJar plugin. Ideally, it should also use some form of local storage, preferentially using some form of SQL.

## User login specifications:

- On first launch of the application, a view will be set up to set the password of the Exco account. Once filled, the current user is redirected to the main login screen
- Only 1 exco account can be created.
- As many Member accounts can be created. Multiple user accounts with the exact user name (case-sensitive) cannot be created.
- On the login screen, there is a button to toggle between the Member and Exco login accounts
- A member has to create their account and login using their Username and Password
- An Exco can login through the password that is entered on the application start

## Member account features:

- A member can create a request for an equipment loan. This request includes the name of equipment, optional details, and the duration of the loan (start date, end date)
- A member cannot request for equipment that has 0 stock.
- When the equipment loan is approved, the member who loaned the item can close this loan request by returning the item with no further reports, returning the item with damage (including an image and a description), or not returning the item due to item being lost (include a description)
- Each loaned equipment can only be associated with one single return status (no damage, damaged or lost)

## Exco account features:

- There is only one Exco account, in which the password will be set the first time the application is installed onto the computer used.
- They can view a list of club members
- They can perform CRUD operations on club members (ie. creating account, view account(s), update account details, delete account(s))
- They can view equipment loan requests made by different member accounts
- They can approve equipment loan requests made by members
- They cannot approve an equipment loan if there is 0 stock of the requested equipment
- They can confirm equipment return status (no damage, damaged or lost);
- If an equipment is returned with no issues, the stock will increase by 1 upon confirmation by the Exco
- If an equipment is returned with damage, the Exco can choose to increase the stock by 0 or 1 depending on their personal assessment of the damage
- If an equipment is lost, the stock will not increase upon confirmation by the Exco
- An Exco can stock take the current equipment numbers. Each equipment type has a quantity associated to it;
- The Exco can perform CRUD operations on the type of equipment and the quantity of each equipment available.
