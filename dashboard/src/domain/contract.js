// Binds the shared contract (single source of truth) to the browser build.
// Pure logic lives in status.js / validation.js so Node can test it without Vite.
import contract from '../../../contract/assignment-status.json';

export default contract;
