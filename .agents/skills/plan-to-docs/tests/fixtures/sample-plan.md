# Notification Preferences Plan

## Goal

Allow signed-in users to control optional notification channels while keeping
critical account notifications deliverable and observable.

## Major Feature 1: Preference Management

- REQ-1.1: A user can view the current email and in-application notification
  preferences for that user's account.
- REQ-1.2: A user can enable or disable each optional channel and save all
  preference changes together.
- REQ-1.3: Critical account notifications must retain at least one enabled
  delivery channel. Invalid updates must leave existing preferences unchanged.

## Major Feature 2: Reliable Delivery

- REQ-2.1: Notification producers submit one channel-independent notification
  request rather than sending directly through a channel.
- REQ-2.2: Delivery retries transient failures without sending the same
  notification more than once through a channel.
- REQ-2.3: Administrators can view the delivery state and last failure reason
  for each attempted channel.

## Constraints

- Existing authentication and authorization boundaries remain unchanged.
- No new notification channel beyond email and in-application delivery is in
  scope.
- The design must include unit and integration test scenarios.
