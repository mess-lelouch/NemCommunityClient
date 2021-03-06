package org.nem.ncc.controller;

import org.nem.ncc.controller.requests.WalletNamePasswordBag;
import org.nem.ncc.controller.viewmodels.WalletViewModel;
import org.nem.ncc.services.*;
import org.nem.ncc.wallet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Handles requests related to the REST resource "wallet".
 */
@RestController
public class WalletController {
	private final WalletServices walletServices;
	private final WalletMapper walletMapper;

	/**
	 * Creates a new transaction controller.
	 *
	 * @param walletServices The wallet services.
	 * @param walletMapper The wallet mapper.
	 */
	@Autowired(required = true)
	public WalletController(
			final WalletServices walletServices,
			final WalletMapper walletMapper) {
		this.walletServices = walletServices;
		this.walletMapper = walletMapper;
	}

	//region create / open / info / close

	/**
	 * Creates a new wallet with one account in it. The wallet is stored
	 * encrypted in the configured wallet directory. The name of the wallet file
	 * is the encoded (URL encoded) version of the wallet name. The provided
	 * password is used to encrypt the wallet.
	 *
	 * @param pair The wallet name and password pair.
	 * @return A view of the created wallet.
	 */
	@RequestMapping(value = "/wallet/create", method = RequestMethod.POST)
	public WalletViewModel create(@RequestBody final WalletNamePasswordPair pair) {
		final Wallet wallet = this.walletServices.create(pair);
		return this.walletMapper.toViewModel(wallet);
	}

	/**
	 * Open a wallet. The wallet is defined by the wallet name. The wallet has
	 * to be located in the wallet storage location. The password must match the
	 * password of the given wallet. For opening a wallet at a different
	 * location the function importWallet has to be used.
	 *
	 * @param pair The wallet name and password pair.
	 * @return A view of the opened wallet.
	 */
	@RequestMapping(value = "/wallet/open", method = RequestMethod.POST)
	public WalletViewModel open(@RequestBody final WalletNamePasswordPair pair) {
		final Wallet wallet = this.walletServices.open(pair);
		return this.walletMapper.toViewModel(wallet);
	}

	/**
	 * Returns information about a wallet that is already open.
	 *
	 * @param name The wallet name.
	 * @return A view of the wallet.
	 */
	@RequestMapping(value = "/wallet/info", method = RequestMethod.POST)
	public WalletViewModel info(@RequestBody final WalletName name) {
		final Wallet wallet = this.walletServices.get(name);
		return this.walletMapper.toViewModel(wallet);
	}

	/**
	 * Closes a wallet by removing it from the list of opened wallets.
	 *
	 * @param name The wallet name.
	 */
	@RequestMapping(value = "/wallet/close", method = RequestMethod.POST)
	public void close(@RequestBody final WalletName name) {
		this.walletServices.close(name);
	}

	//endregion

	//region changePassword / changeName

	/**
	 * Changes the password of a wallet.
	 *
	 * @param bag The request parameters.
	 */
	@RequestMapping(value = "/wallet/password/change", method = RequestMethod.POST)
	public void changePassword(@RequestBody final WalletNamePasswordBag bag) {
		this.walletServices.move(bag, new WalletNamePasswordPair(bag.getName(), bag.getNewPassword()));
	}

	/**
	 * Changes the password of a wallet.
	 *
	 * @param bag The request parameters.
	 */
	@RequestMapping(value = "/wallet/name/change", method = RequestMethod.POST)
	public void changeName(@RequestBody final WalletNamePasswordBag bag) {
		this.walletServices.move(bag, new WalletNamePasswordPair(bag.getNewName(), bag.getPassword()));
	}

	//endregion
}
