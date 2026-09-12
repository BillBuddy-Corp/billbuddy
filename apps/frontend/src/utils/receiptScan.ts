import * as ImagePicker from 'expo-image-picker';
import { ActionSheetIOS, Alert, Platform } from 'react-native';

import { ReceiptScan, scanReceipt } from '../api/billScanner';
import { uploadFile } from '../api/files';
import { getErrorMessage } from './errors';

type PickedAsset = { uri: string; fileName: string | null | undefined; mimeType: string | null | undefined };

function promptImageSource(title: string): Promise<'camera' | 'library' | null> {
  return new Promise((resolve) => {
    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        { title, options: ['Take Photo', 'Choose from Library', 'Cancel'], cancelButtonIndex: 2 },
        (index) => resolve(index === 0 ? 'camera' : index === 1 ? 'library' : null)
      );
    } else {
      Alert.alert(title, undefined, [
        { text: 'Take Photo', onPress: () => resolve('camera') },
        { text: 'Choose from Library', onPress: () => resolve('library') },
        { text: 'Cancel', style: 'cancel', onPress: () => resolve(null) },
      ]);
    }
  });
}

async function pickImage(source: 'camera' | 'library'): Promise<PickedAsset | null> {
  const permission =
    source === 'camera'
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) {
    Alert.alert('Permission needed', `Allow access to your ${source === 'camera' ? 'camera' : 'photos'} to continue.`);
    return null;
  }
  const result =
    source === 'camera'
      ? await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 })
      : await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
  if (result.canceled || result.assets.length === 0) return null;
  const asset = result.assets[0];
  return { uri: asset.uri, fileName: asset.fileName, mimeType: asset.mimeType };
}

export type UploadedReceipt = { fileId: number; url: string };

// Picks + uploads a photo with no OCR -- for attaching a receipt to an
// expense purely for the record (viewable later), independent of the
// itemized-scan flow below.
export async function pickAndUploadReceipt(): Promise<UploadedReceipt | null> {
  const source = await promptImageSource('Add a photo');
  if (!source) return null;
  const asset = await pickImage(source);
  if (!asset) return null;
  try {
    const uploaded = await uploadFile(asset.uri, asset.fileName ?? 'receipt.jpg', asset.mimeType ?? 'image/jpeg');
    return { fileId: uploaded.id, url: uploaded.url };
  } catch (err) {
    Alert.alert('Could not upload photo', getErrorMessage(err));
    return null;
  }
}

export type ScannedReceipt = { fileId: number; scan: ReceiptScan };

// Picks + uploads + OCR-scans a photo in one go, so tapping "Scan a
// receipt" goes straight to the native Take Photo/Choose from Library
// picker with no screen in between -- the caller navigates only after a
// successful scan.
export async function pickAndScanReceipt(): Promise<ScannedReceipt | null> {
  const source = await promptImageSource('Scan a receipt');
  if (!source) return null;
  const asset = await pickImage(source);
  if (!asset) return null;
  try {
    const uploaded = await uploadFile(asset.uri, asset.fileName ?? 'receipt.jpg', asset.mimeType ?? 'image/jpeg');
    const scan = await scanReceipt(uploaded.id);
    if (scan.needsReview || scan.amount === null || scan.items.length === 0) {
      Alert.alert("Couldn't read this receipt reliably", 'Add the expense manually instead, or try a clearer photo.');
      return null;
    }
    return { fileId: uploaded.id, scan };
  } catch (err) {
    Alert.alert('Could not scan receipt', getErrorMessage(err));
    return null;
  }
}
