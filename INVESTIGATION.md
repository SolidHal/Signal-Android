interesting commits:
https://github.com/signalapp/Signal-Android/commit/720322862632203624845f48566610746817b376
https://github.com/signalapp/Signal-Android/commit/c548816daa9a1b355ee346884169bb266b7403f8
https://github.com/signalapp/Signal-Android/commit/22c396067d21d219cd7d968afaca12fb7a61287a



rough linking process is:
1) show QR code on secondary device
2) scan QR code on primary device
3) set name on secondary device
4) select Finish on secondary device


## IOS secondary device registration process

// user presses setup as linked device  on secondary device
didConfirmSecondaryDevice
// user scans QR code on secondary device
awaitProvisioning
// we receive a provisioning message from the primary device
// we ask the user for a device name
// user presses "finish"
// which then kicks off "didTapFinalizeLinking"
// which eventually calls the "verifySecondaryDevice" api AND WE HAVE THIS ON ANDROID!


so, we need:
1) some way to show/generate a QR code on the secondary device
  - fast solution is to figure out the info in the QR code, and feed that to an external QR generator for now
  
2) a button to hit for "setup as secondary device"
  - shows QR code/generated secondary device info from (1)
  - UI to enter device name - hard code for now
  - UI to hit "finish"


need to take a look at the `Fragment` and `ViewModel` classes to add a button. 

```

  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode,
                                                    int signalProtocolRegistrationId,
                                                    boolean fetchesMessages,
                                                    byte[] unidentifiedAccessKey,
                                                    boolean unrestrictedUnidentifiedAccess,
                                                    AccountAttributes.Capabilities capabilities,
                                                    boolean discoverableByPhoneNumber,
                                                    byte[] encryptedDeviceName)

```

seems to be the api we want to take advantage of. Unfortunately, there are no users of the api :/


```
rg "verifySecondaryDevice"
libsignal/service/src/main/java/org/whispersystems/signalservice/api/SignalServiceAccountManager.java
342:  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode,
365:    return this.pushServiceSocket.verifySecondaryDevice(verificationCode, accountAttributes);

libsignal/service/src/main/java/org/whispersystems/signalservice/internal/push/PushServiceSocket.java
403:  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode, AccountAttributes accountAttributes) throws IOException {
```


we can look at the neighboring APIs:

## SignalServiceAccountManager.verifyAccountWithRegistrationLockPin
```

  public ServiceResponse<VerifyAccountResponse> verifyAccountWithRegistrationLockPin(String verificationCode,
                                                                                     int signalProtocolRegistrationId,
                                                                                     boolean fetchesMessages,
                                                                                     String registrationLock,
                                                                                     byte[] unidentifiedAccessKey,
                                                                                     boolean unrestrictedUnidentifiedAccess,
                                                                                     AccountAttributes.Capabilities capabilities,
                                                                                     boolean discoverableByPhoneNumber)
  {

```

which has one user:

```
rg "verifyAccountWithRegistrationLockPin"
app/src/main/java/org/thoughtcrime/securesms/registration/VerifyAccountRepository.kt
94:        val response: ServiceResponse<VerifyAccountResponse> = accountManager.verifyAccountWithRegistrationLockPin(

```

lets go up the stack:

```
SignalServiceAccountManager.verifyAccountWithRegistrationLockPin()
VerifyAccountRepository.verifyAccountWithPin()
RegistrationViewModel.verifyAccountWithRegistrationLock()
BaseRegistrationViewModel.verifyCodeAndRegisterAccountWithRegistrationLock()
BaseRegistrationLockFragment.handlePinEntry()
```


## SignalServiceAccountManager.verifyAccount

```

  public ServiceResponse<VerifyAccountResponse> verifyAccount(String verificationCode,
                                                              int signalProtocolRegistrationId,
                                                              boolean fetchesMessages,
                                                              byte[] unidentifiedAccessKey,
                                                              boolean unrestrictedUnidentifiedAccess,
                                                              AccountAttributes.Capabilities capabilities,
                                                              boolean discoverableByPhoneNumber)
```

which has a few interesting users:
```
❯ rg "verifyAccount\("
app/src/main/java/org/thoughtcrime/securesms/registration/viewmodel/RegistrationViewModel.java
107:    return verifyAccountRepository.verifyAccount(getRegistrationData());

app/src/main/java/org/thoughtcrime/securesms/registration/VerifyAccountRepository.kt
54:  fun verifyAccount(registrationData: RegistrationData): Single<ServiceResponse<VerifyAccountResponse>> {
66:      accountManager.verifyAccount(

```



## SignalServiceAccountManager.requestSmsVerificationCode

```
  public ServiceResponse<RequestVerificationCodeResponse> requestSmsVerificationCode(boolean androidSmsRetrieverSupported, Optional<String> captchaToken, Optional<String> challenge, Optional<String> fcmToken) {

```

which also has an interesting user:

```
❯ rg "requestSmsVerificationCode"
app/src/main/java/org/thoughtcrime/securesms/registration/VerifyAccountRepository.kt
49:        accountManager.requestSmsVerificationCode(mode.isSmsRetrieverSupported, Optional.ofNullable(captchaToken), pushChallenge, fcmToken)
```




# Lets look at the ios side

## other interesting things:

Signal/src/ViewControllers/Registration/SecondaryLinking/
```
❯ ls Signal/src/ViewControllers/Registration/SecondaryLinking/
ProvisioningController.swift              SecondaryLinkingQRCodeViewController.swift
SecondaryLinkingPrepViewController.swift  SecondaryLinkingSetDeviceNameViewController.swift
```

## verifySecondaryDevice

```
❯ rg "verifySecondaryDevice"
SignalServiceKit/src/Account/AccountServiceClient.swift
62:    public func verifySecondaryDevice(verificationCode: String,


Signal/src/Models/AccountManager.swift
311:            return accountServiceClient.verifySecondaryDevice(verificationCode: provisionMessage.provisioningCode,
```
which is called in:

```
completeSecondaryLinking
```

```
❯ rg "completeSecondaryLinking"
Signal/src/ViewControllers/Registration/SecondaryLinking/ProvisioningController.swift
207:            return self.accountManager.completeSecondaryLinking(provisionMessage: provisionMessage,
```

which is called in:


```
    func didSetDeviceName(_ deviceName: String, from viewController: UIViewController) {
        let backgroundBlock: (ModalActivityIndicatorViewController) -> Void = { modal in
            self.completeLinking(deviceName: deviceName).done {
                modal.dismiss {
                    self.onboardingController.linkingDidComplete(from: viewController)
                }
            }.catch { error in
```

which is called in:

```
    func didTapFinalizeLinking() {
        guard let deviceName = validateDeviceName() else {
            return
        }

        provisioningController.didSetDeviceName(String(deviceName), from: self)
    }
```


## IOS registration process

// user presses link device
didConfirmSecondaryDevice
// user scans QR code
awaitProvisioning
// we receive a provisioning message from the primary device
// we ask the user for a device name
// user presses "finish"
// which then kicks off "didTapFinalizeLinking"
