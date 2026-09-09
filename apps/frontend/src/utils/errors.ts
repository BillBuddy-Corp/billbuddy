import { isAxiosError } from 'axios';

export function getErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    const message = error.response?.data?.message;
    if (typeof message === 'string') {
      return message;
    }
  }
  if (__DEV__) {
    console.error('Unhandled error', error);
  }
  return 'Something went wrong. Please try again.';
}
