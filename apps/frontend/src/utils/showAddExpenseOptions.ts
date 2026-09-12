import { ActionSheetIOS, Alert, Platform } from 'react-native';

// Splitwise's own "+" button offers this same choice up front -- scanning a
// receipt was previously only reachable via a small icon inside the manual
// Add Expense form itself, easy to miss entirely.
export function showAddExpenseOptions(onScan: () => void, onManual: () => void) {
  if (Platform.OS === 'ios') {
    ActionSheetIOS.showActionSheetWithOptions(
      { options: ['Scan a receipt', 'Add expense manually', 'Cancel'], cancelButtonIndex: 2 },
      (index) => {
        if (index === 0) onScan();
        if (index === 1) onManual();
      }
    );
  } else {
    Alert.alert('Add expense', undefined, [
      { text: 'Scan a receipt', onPress: onScan },
      { text: 'Add expense manually', onPress: onManual },
      { text: 'Cancel', style: 'cancel' },
    ]);
  }
}
