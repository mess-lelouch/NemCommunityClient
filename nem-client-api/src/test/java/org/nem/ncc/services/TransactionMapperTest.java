package org.nem.ncc.services;

import org.hamcrest.core.*;
import org.junit.*;
import org.mockito.Mockito;
import org.nem.core.crypto.KeyPair;
import org.nem.core.model.*;
import org.nem.core.model.primitive.Amount;
import org.nem.core.serialization.AccountLookup;
import org.nem.core.time.*;
import org.nem.core.utils.StringEncoder;
import org.nem.ncc.controller.requests.*;
import org.nem.ncc.controller.viewmodels.PartialTransferInformationViewModel;
import org.nem.ncc.exceptions.NccException;
import org.nem.ncc.test.*;
import org.nem.ncc.wallet.*;

public class TransactionMapperTest {

	//region toModel

	@Test
	public void canMapFromTransferSendRequestToModel() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		final TransferSendRequest request = createSendRequestWithoutMessage(context, "p");
		final TransferTransaction model = (TransferTransaction)context.mapper.toModel(request);

		// Assert:
		Assert.assertThat(model.getFee(), IsEqual.equalTo(Amount.fromNem(2)));
		Assert.assertThat(model.getSigner(), IsEqual.equalTo(context.signer));
		Assert.assertThat(model.getTimeStamp(), IsEqual.equalTo(new TimeInstant(124)));
		Assert.assertThat(model.getDeadline(), IsEqual.equalTo(new TimeInstant(124 + 5 * 60 * 60)));

		Assert.assertThat(model.getRecipient(), IsEqual.equalTo(context.recipient));
		Assert.assertThat(model.getMessage(), IsNull.nullValue());
		Assert.assertThat(model.getAmount(), IsEqual.equalTo(Amount.fromNem(7)));
	}

	@Test
	public void canMapFromRemoteHarvestRequestToModel() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		final TransferImportanceRequest request = createRemoteHarvestRequest(context, "p");
		final ImportanceTransferTransaction model = (ImportanceTransferTransaction)context.mapper.toModel(request, ImportanceTransferTransaction.Mode.Activate);

		// Assert:
		Assert.assertThat(
				model.getRemote().getKeyPair().getPrivateKey(),
				IsEqual.equalTo(context.account.getRemoteHarvestingPrivateKey())); // the remote address for harvesting
		Assert.assertThat(model.getSigner(), IsEqual.equalTo(context.signer));
		Assert.assertThat(model.getMode(), IsEqual.equalTo(ImportanceTransferTransaction.Mode.Activate));
		Assert.assertThat(model.getTimeStamp(), IsEqual.equalTo(new TimeInstant(124)));
		Assert.assertThat(model.getDeadline(), IsEqual.equalTo(new TimeInstant(124 + 7 * 60 * 60)));
	}

	//region wallet services delegation

	@Test
	public void walletIsOpenedWhenMappingFromTransferSendRequestWithPassword() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		final TransferSendRequest request = createSendRequestWithoutMessage(context, "p");
		context.mapper.toModel(request);

		// Assert:
		Mockito.verify(context.walletServices, Mockito.times(1)).open(new WalletNamePasswordPair("w", "p"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void mappingFromTransferSendRequestFailsWhenWalletPasswordIsNotProvided() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		context.mapper.toModel(createSendRequestWithoutMessage(context, null));
	}

	@Test(expected = IllegalArgumentException.class)
	public void mappingFromTransferSendRequestFailsWhenWalletCannotBeOpened() {
		// Arrange:
		final TestContext context = new TestContext();
		Mockito.when(context.walletServices.open(Mockito.any())).thenReturn(null);

		// Act:
		context.mapper.toModel(createSendRequestWithoutMessage(context, "p"));
	}

	//endregion

	//region message mapping

	@Test
	public void canMapTransactionWithPlainMessage() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		final TransferSendRequest request = createSendRequestWithMessage(context, "nem rules!", false);
		final TransferTransaction model = (TransferTransaction)context.mapper.toModel(request);

		// Assert:
		Assert.assertThat(
				model.getMessage().getDecodedPayload(),
				IsEqual.equalTo(StringEncoder.getBytes("nem rules!")));
		Assert.assertThat(
				model.getMessage().getEncodedPayload(),
				IsEqual.equalTo(StringEncoder.getBytes("nem rules!")));
	}

	@Test
	public void canMapTransactionWithSecureMessage() {
		// Arrange:
		final TestContext context = new TestContext();

		// Act:
		final TransferSendRequest request = createSendRequestWithMessage(context, "nem rules!", true);
		final TransferTransaction model = (TransferTransaction)context.mapper.toModel(request);

		// Assert:
		Assert.assertThat(
				model.getMessage().getDecodedPayload(),
				IsEqual.equalTo(StringEncoder.getBytes("nem rules!")));
		Assert.assertThat(
				model.getMessage().getEncodedPayload(),
				IsNot.not(IsEqual.equalTo(StringEncoder.getBytes("nem rules!"))));
	}

	@Test
	public void cannotMapTransactionWithSecureMessageWhenRecipientPublicKeyIsUnknown() {
		// Arrange:
		final TestContext context = new TestContext(new Account(Address.fromEncoded("foo")));

		// Act:
		final TransferSendRequest request = createSendRequestWithMessage(context, "nem rules!", true);
		ExceptionAssert.assertThrowsNccException(
				v -> context.mapper.toModel(request),
				NccException.Code.NO_PUBLIC_KEY);
	}

	//endregion

	//endregion

	//region viewModel

	//region can calculate fee without message

	@Test
	public void canCalculateFeeWhenRecipientIsNullWithoutMessage() {
		// Assert:
		assertCanCalculateFeeWithoutMessage(new Account(new KeyPair()), null, false);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithoutMessage() {
		// Assert:
		final Account recipient = new Account(new KeyPair());
		assertCanCalculateFeeWithoutMessage(recipient, recipient.getAddress(), true);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithoutPublicKeyAndWithoutMessage() {
		// Assert:
		final Account recipient = new Account(Utils.generateRandomAddress());
		assertCanCalculateFeeWithoutMessage(recipient, recipient.getAddress(), false);
	}

	private static void assertCanCalculateFeeWithoutMessage(
			final Account recipient,
			final Address recipientAddress,
			final boolean isEncryptionSupported) {
		// Arrange:
		final TestContext context = new TestContext(recipient);
		final PartialTransferInformationRequest request = new PartialTransferInformationRequest(recipientAddress, Amount.fromNem(10), null, false);

		// Act:
		final PartialTransferInformationViewModel viewModel = context.mapper.toViewModel(request);

		// Assert:
		Assert.assertThat(viewModel.getFee(), IsEqual.equalTo(Amount.fromNem(1)));
		Assert.assertThat(viewModel.isEncryptionSupported(), IsEqual.equalTo(isEncryptionSupported));
	}

	//endregion

	//region can calculate fee with plain message

	@Test
	public void canCalculateFeeWhenRecipientIsNullWithPlainMessage() {
		// Assert:
		assertCanCalculateFeeWithPlainMessage(new Account(new KeyPair()), null, false);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithPlainMessage() {
		// Assert:
		final Account recipient = new Account(new KeyPair());
		assertCanCalculateFeeWithPlainMessage(recipient, recipient.getAddress(), true);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithoutPublicKeyAndWithPlainMessage() {
		// Assert:
		final Account recipient = new Account(Utils.generateRandomAddress());
		assertCanCalculateFeeWithPlainMessage(recipient, recipient.getAddress(), false);
	}

	private static void assertCanCalculateFeeWithPlainMessage(
			final Account recipient,
			final Address recipientAddress,
			final boolean isEncryptionSupported) {
		assertCanCalculateFeeWithMessage(recipient, recipientAddress, isEncryptionSupported, false);
	}

	//endregion

	//region can calculate fee with secure message

	@Test
	public void canCalculateFeeWhenRecipientIsNullWithSecureMessage() {
		// Assert:
		assertCanCalculateFeeWithSecureMessage(new Account(new KeyPair()), null, false);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithSecureMessage() {
		// Assert:
		final Account recipient = new Account(new KeyPair());
		assertCanCalculateFeeWithSecureMessage(recipient, recipient.getAddress(), true);
	}

	@Test
	public void canCalculateFeeWhenRecipientIsNotNullWithoutPublicKeyAndWithSecureMessage() {
		// Assert:
		final Account recipient = new Account(Utils.generateRandomAddress());
		assertCanCalculateFeeWithSecureMessage(recipient, recipient.getAddress(), false);
	}

	private static void assertCanCalculateFeeWithSecureMessage(
			final Account recipient,
			final Address recipientAddress,
			final boolean isEncryptionSupported) {
		assertCanCalculateFeeWithMessage(recipient, recipientAddress, isEncryptionSupported, true);
	}

	private static void assertCanCalculateFeeWithMessage(
			final Account recipient,
			final Address recipientAddress,
			final boolean isEncryptionSupported,
			final boolean isSecure) {
		// Arrange:
		final TestContext context = new TestContext(recipient);
		final PartialTransferInformationRequest request = new PartialTransferInformationRequest(
				recipientAddress,
				Amount.fromNem(10),
				"hi nem",
				isSecure);

		// Act:
		final PartialTransferInformationViewModel viewModel = context.mapper.toViewModel(request);

		// Assert:
		Assert.assertThat(viewModel.getFee(), IsEqual.equalTo(Amount.fromNem(2)));
		Assert.assertThat(viewModel.isEncryptionSupported(), IsEqual.equalTo(isEncryptionSupported));
	}

	//endregion

	//region can calculate fee without amount

	@Test
	public void canCalculateFeeWhenAmountIsNotProvidedWithoutMessage() {
		// Arrange:
		final TestContext context = new TestContext();
		final Address address = context.recipient.getAddress();
		final PartialTransferInformationRequest request = new PartialTransferInformationRequest(address, null, null, false);

		// Act:
		final PartialTransferInformationViewModel viewModel = context.mapper.toViewModel(request);

		// Assert:
		Assert.assertThat(viewModel.getFee(), IsEqual.equalTo(Amount.fromNem(1)));
		Assert.assertThat(viewModel.isEncryptionSupported(), IsEqual.equalTo(true));
	}

	@Test
	public void canCalculateFeeWhenAmountIsNotProvidedWithMessage() {
		// Arrange:
		final TestContext context = new TestContext();
		final Address address = context.recipient.getAddress();
		final PartialTransferInformationRequest request = new PartialTransferInformationRequest(address, null, "hi nem", false);

		// Act:
		final PartialTransferInformationViewModel viewModel = context.mapper.toViewModel(request);

		// Assert:
		Assert.assertThat(viewModel.getFee(), IsEqual.equalTo(Amount.fromNem(2)));
		Assert.assertThat(viewModel.isEncryptionSupported(), IsEqual.equalTo(true));
	}

	//endregion

	//endregion

	private static TransferSendRequest createSendRequestWithMessage(final TestContext context, final String message, final boolean shouldEncrypt) {
		return new TransferSendRequest(
				new WalletName("w"),
				context.signer.getAddress(), // must be a valid address: Address.fromEncoded("a"),
				context.recipient.getAddress(), // Address.fromEncoded("r"),
				Amount.fromNem(7),
				message,
				shouldEncrypt,
				5,
				new WalletPassword("p"),
				Amount.fromNem(2));
	}

	private static TransferSendRequest createSendRequestWithoutMessage(final TestContext context, final String password) {
		return new TransferSendRequest(
				new WalletName("w"),
				context.signer.getAddress(), // must be a valid address: Address.fromEncoded("a"),
				context.recipient.getAddress(), // Address.fromEncoded("r"),
				Amount.fromNem(7),
				null,
				false,
				5,
				null == password ? null : new WalletPassword(password),
				Amount.fromNem(2));
	}

	private static TransferImportanceRequest createRemoteHarvestRequest(final TestContext context, final String password) {
		return new TransferImportanceRequest(
				context.signer.getAddress(), // must be a valid address: Address.fromEncoded("a"),
				new WalletName("w"),
				null == password ? null : new WalletPassword(password),
				7);
	}

	private static class TestContext {
		private final WalletServices walletServices = Mockito.mock(WalletServices.class);
		private final AccountLookup accountLookup = Mockito.mock(AccountLookup.class);
		private final TimeProvider timeProvider = Mockito.mock(TimeProvider.class);
		private final TransactionMapper mapper = new TransactionMapper(
				this.walletServices,
				this.accountLookup,
				this.timeProvider);

		private final Account signer = Utils.generateRandomAccount();
		private final WalletAccount account = new WalletAccount(Utils.generateRandomAccount().getKeyPair().getPrivateKey());
		private final Account recipient;
		private final Wallet wallet = Mockito.mock(Wallet.class);

		public TestContext() {
			this(Utils.generateRandomAccount());
		}

		public TestContext(final Account recipient) {
			this.recipient = recipient;

			Mockito.when(this.timeProvider.getCurrentTime()).thenReturn(new TimeInstant(124));
			Mockito.when(this.accountLookup.findByAddress(this.recipient.getAddress())).thenReturn(this.recipient);
			Mockito.when(this.wallet.getAccountPrivateKey(this.signer.getAddress()))
					.thenReturn(this.signer.getKeyPair().getPrivateKey());
			Mockito.when(this.wallet.tryGetWalletAccount(this.signer.getAddress()))
					.thenReturn(this.account);

			final WalletNamePasswordPair pair = new WalletNamePasswordPair("w", "p");
			Mockito.when(this.walletServices.open(pair)).thenReturn(this.wallet);
		}
	}
}