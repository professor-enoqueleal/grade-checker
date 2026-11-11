import { t, type MessageSchema } from './index';

/**
 * Text of an API error in the user's language (openspec capability serverless-i18n). The backend answers
 * { error: code, message } in English. A code with one meaning is translated by the code; a code that covers several
 * cases (bad_request, not_found, forbidden...) by the message. Unknown errors keep the message of the backend.
 */
type ErrorKey = keyof MessageSchema['errors'];

const BY_CODE: Record<string, ErrorKey> = {
  network: 'network',
  has_aliases: 'hasAliases',
  already_member: 'alreadyMember',
  invalid_identity: 'invalidIdentity',
  invalid_repository: 'invalidRepository',
};

const BY_MESSAGE: Record<string, ErrorKey> = {
  'Unexpected error.': 'unexpected',
  'Sign in to continue.': 'signIn',
  'Group not found.': 'groupNotFound',
  'The group name is required.': 'nameRequired',
  'Enter the URL of a GitHub repository (https://github.com/owner/repository).': 'repositoryUrl',
  'This repository is already registered.': 'alreadyRegistered',
  'This repository is already registered in an archived group. Restore it instead.': 'alreadyArchived',
  'A valid GitHub login is required.': 'loginRequired',
  'This GitHub user does not exist.': 'invalidLogin',
  'This user is not a member of the group.': 'notMember',
  'The login must be a student of this group.': 'notAStudent',
  'This identity is not linked.': 'notLinked',
  "The 'author' parameter is required.": 'authorRequired',
  "The 'id' parameter is required.": 'idRequired',
  'Only administrators can manage groups.': 'groupsForbidden',
  'Only administrators can manage members.': 'membersForbidden',
  'Only administrators can link identities.': 'aliasesForbidden',
  'Only administrators can see class grades.': 'gradesForbidden',
  'Could not calculate the grades of this group.': 'gradebookGroup',
  'Please enter a personal access token.': 'tokenMissing',
  'GitHub rejected this personal access token.': 'tokenRejected',
  'Could not verify the token with GitHub. Please try again.': 'tokenUnverified',
  'Could not connect to GitHub. Please try again.': 'gitHubUnreachable',
  'Could not reach GitHub.': 'gitHubUnreachable',
};

const REQUEST_FAILED = /^Request failed \(HTTP (\d+)\)\.$/;

export function apiErrorText(code: string, message: string): string {
  const key = BY_MESSAGE[message] ?? BY_CODE[code];
  if (key) return t(`errors.${key}`);
  const failed = REQUEST_FAILED.exec(message);
  if (failed) return t('errors.requestFailed', { status: failed[1] });
  return message;
}
