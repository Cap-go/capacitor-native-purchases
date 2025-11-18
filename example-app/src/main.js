import './style.css';
import { NativePurchases } from '@capgo/native-purchases';

const plugin = NativePurchases;
const state = {};

const actions = [
  {
    id: 'get-plugin-version',
    label: 'Get plugin version',
    description: 'Returns the native RevenueCat wrapper version.',
    inputs: [],
    run: async (values) => {
      return await plugin.getPluginVersion();
    },
  },
  {
    id: 'is-billing-supported',
    label: 'Check billing support',
    description: 'Determines whether billing is available on this device.',
    inputs: [],
    run: async (values) => {
      return await plugin.isBillingSupported();
    },
  },
  {
    id: 'get-products',
    label: 'Fetch products',
    description: 'Fetches metadata for product identifiers. Separate multiple IDs with commas.',
    inputs: [
      {
        name: 'productIdentifiers',
        label: 'Product identifiers',
        type: 'text',
        value: 'premium_upgrade',
      },
    ],
    run: async (values) => {
      const raw = values.productIdentifiers || '';
      const productIdentifiers = raw
        .split(',')
        .map((id) => id.trim())
        .filter(Boolean);
      if (!productIdentifiers.length) {
        throw new Error('Provide at least one product id.');
      }
      return await plugin.getProducts({ productIdentifiers });
    },
  },
  {
    id: 'get-purchases',
    label: 'Get purchases',
    description: 'Retrieves all known transactions/entitlements (optionally filtered by appAccountToken).',
    inputs: [
      {
        name: 'appAccountToken',
        label: 'App account token (optional)',
        type: 'text',
        placeholder: 'UUID string',
      },
    ],
    run: async (values) => {
      const token = values.appAccountToken?.trim();
      const payload = token ? { appAccountToken: token } : {};
      return await plugin.getPurchases(payload);
    },
  },
];

const actionSelect = document.getElementById('action-select');
const formContainer = document.getElementById('action-form');
const descriptionBox = document.getElementById('action-description');
const runButton = document.getElementById('run-action');
const output = document.getElementById('plugin-output');
const eventLog = document.getElementById('event-log');

function appendEventLog(eventName, payload) {
  if (!eventLog) {
    return;
  }
  const timestamp = new Date().toISOString();
  let entry = `[${timestamp}] ${eventName}`;
  if (payload !== undefined) {
    entry += `\n${JSON.stringify(payload, null, 2)}`;
  }
  const previous =
    !eventLog.textContent || eventLog.textContent === 'Listeners not registered yet.' ? '' : eventLog.textContent;
  eventLog.textContent = previous ? `${entry}\n\n${previous}` : entry;
}

function buildForm(action) {
  formContainer.innerHTML = '';
  if (!action.inputs || !action.inputs.length) {
    const note = document.createElement('p');
    note.className = 'no-input-note';
    note.textContent = 'This action does not require any inputs.';
    formContainer.appendChild(note);
    return;
  }
  action.inputs.forEach((input) => {
    const fieldWrapper = document.createElement('div');
    fieldWrapper.className = input.type === 'checkbox' ? 'form-field inline' : 'form-field';

    const label = document.createElement('label');
    label.textContent = input.label;
    label.htmlFor = `field-${input.name}`;

    let field;
    switch (input.type) {
      case 'textarea': {
        field = document.createElement('textarea');
        field.rows = input.rows || 4;
        break;
      }
      case 'select': {
        field = document.createElement('select');
        (input.options || []).forEach((option) => {
          const opt = document.createElement('option');
          opt.value = option.value;
          opt.textContent = option.label;
          if (input.value !== undefined && option.value === input.value) {
            opt.selected = true;
          }
          field.appendChild(opt);
        });
        break;
      }
      case 'checkbox': {
        field = document.createElement('input');
        field.type = 'checkbox';
        field.checked = Boolean(input.value);
        break;
      }
      case 'number': {
        field = document.createElement('input');
        field.type = 'number';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
        break;
      }
      default: {
        field = document.createElement('input');
        field.type = 'text';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
      }
    }

    field.id = `field-${input.name}`;
    field.name = input.name;
    field.dataset.type = input.type || 'text';

    if (input.placeholder && input.type !== 'checkbox') {
      field.placeholder = input.placeholder;
    }

    if (input.type === 'checkbox') {
      fieldWrapper.appendChild(field);
      fieldWrapper.appendChild(label);
    } else {
      fieldWrapper.appendChild(label);
      fieldWrapper.appendChild(field);
    }

    formContainer.appendChild(fieldWrapper);
  });
}

function getFormValues(action) {
  const values = {};
  (action.inputs || []).forEach((input) => {
    const field = document.getElementById(`field-${input.name}`);
    if (!field) return;
    switch (input.type) {
      case 'number': {
        values[input.name] = field.value === '' ? null : Number(field.value);
        break;
      }
      case 'checkbox': {
        values[input.name] = field.checked;
        break;
      }
      default: {
        values[input.name] = field.value;
      }
    }
  });
  return values;
}

function setAction(action) {
  descriptionBox.textContent = action.description || '';
  buildForm(action);
  output.textContent = 'Ready to run the selected action.';
}

function populateActions() {
  actionSelect.innerHTML = '';
  actions.forEach((action) => {
    const option = document.createElement('option');
    option.value = action.id;
    option.textContent = action.label;
    actionSelect.appendChild(option);
  });
  setAction(actions[0]);
}

actionSelect.addEventListener('change', () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (action) {
    setAction(action);
  }
});

runButton.addEventListener('click', async () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (!action) return;
  const values = getFormValues(action);
  try {
    const result = await action.run(values);
    if (result === undefined) {
      output.textContent = 'Action completed.';
    } else if (typeof result === 'string') {
      output.textContent = result;
    } else {
      output.textContent = JSON.stringify(result, null, 2);
    }
  } catch (error) {
    output.textContent = `Error: ${error?.message ?? error}`;
  }
});

async function setupTransactionListeners() {
  try {
    const updatedListener = await plugin.addListener('transactionUpdated', (payload) => {
      appendEventLog('transactionUpdated', payload);
    });
    const failedListener = await plugin.addListener('transactionVerificationFailed', (payload) => {
      appendEventLog('transactionVerificationFailed', payload);
    });

    window.addEventListener('beforeunload', () => {
      updatedListener.remove();
      failedListener.remove();
    });

    appendEventLog('Transaction listeners registered');
  } catch (error) {
    appendEventLog('listenerRegistrationFailed', { message: error?.message ?? String(error) });
    console.error('Failed to register transaction listeners', error);
  }
}

setupTransactionListeners();
populateActions();
