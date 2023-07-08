package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.*;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StakeTxIT extends QuickTxBaseIT {
    BackendService backendService;
    UtxoSupplier utxoSupplier;
    ProtocolParamsSupplier protocolParamsSupplier;
    Account sender1;
    Account sender2;

    String sender1Addr;
    String sender2Addr;

    String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

    String poolId;

    String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
    PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

    String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
    PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

    String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
    String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();


    QuickTxBuilder quickTxBuilder;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        quickTxBuilder = new QuickTxBuilder(backendService);

        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        //addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
        String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
        sender2 = new Account(Networks.testnet(), sender2Mnemonic);
        sender2Addr = sender2.baseAddress();

        if (backendType.equals(DEVKIT)) {
            poolId = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";
        } else {
            poolId = "pool1vqq4hdwrh442u97e2jh6k4xuscs3x5mqjjrn8daj36y7gt2rj85";
        }
    }

    @Test
    @Order(1)
    void stakeAddressRegistration() {
        //De-register all stake addresses if required
        _deRegisterStakeKeys();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .payToAddress(sender1Addr, Amount.ada(4.3))
                .registerStakeAddress(sender2Addr)
                .registerStakeAddress(sender1Addr)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(2)
    void stakeAddressDeRegistration() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver1, Amount.ada(3.5))
                .payToAddress(sender1Addr, Amount.ada(4.0))
                .deregisterStakeAddress(sender2Addr)
                .deregisterStakeAddress(sender1Addr, sender2Addr)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1, sender2))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(3)
    void stakeAddressRegistration_onlyRegistration() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .registerStakeAddress(sender1Addr)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(4)
    void stakeAddressDeRegistration_onlyRegistration() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .deregisterStakeAddress(AddressProvider.getStakeAddress(new Address(sender1Addr)))
                .attachMetadata(MessageMetadata.create().add("This is a stake deregistration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(5)
    void scriptStakeAddress_registration() {
        deregisterScriptsStakeKeys();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .registerStakeAddress(scriptStakeAddress1)
                .registerStakeAddress(scriptStakeAddress2)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(6)
    void scriptStakeAddress_deRegistration() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(scriptStakeAddress1, BigIntPlutusData.of(1))
                .deregisterStakeAddress(scriptStakeAddress2, BigIntPlutusData.of(2))
                .attachMetadata(MessageMetadata.create().add("This is a script stake address deregistration tx"))
                .attachCertificateValidator(plutusScript1)
                .attachCertificateValidator(plutusScript2);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(7)
    void scriptStakeAddress_registration_withChangeAddress() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //Registration
        Tx tx = new Tx()
                .registerStakeAddress(scriptStakeAddress1)
                .registerStakeAddress(scriptStakeAddress2)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(8)
    void scriptStakeAddress_deregistration_withChangeAddress() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        //DeRegistration
        ScriptTx deregTx = new ScriptTx()
                .deregisterStakeAddress(scriptStakeAddress1, BigIntPlutusData.of(1))
                .deregisterStakeAddress(scriptStakeAddress2, BigIntPlutusData.of(2), sender2Addr)
                .attachMetadata(MessageMetadata.create().add("This is a script stake address deregistration tx"))
                .attachCertificateValidator(plutusScript1)
                .attachCertificateValidator(plutusScript2);

        Result<String> deRegResult = quickTxBuilder.compose(deregTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(deRegResult);
        assertTrue(deRegResult.isSuccessful());

        checkIfUtxoAvailable(deRegResult.getValue(), sender1Addr);
    }

    @Test
    @Order(9)
    void stakeDelegation_scriptStakeKeys() {
        registerScriptsStakeKeys();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //Delegation
        ScriptTx delegTx = new ScriptTx()
                .delegateTo(new Address(scriptStakeAddress1), poolId, BigIntPlutusData.of(1))
                .delegateTo(new Address(scriptStakeAddress2), poolId, BigIntPlutusData.of(2))
                .attachMetadata(MessageMetadata.create().add("This is a delegation transaction"))
                .attachCertificateValidator(plutusScript1)
                .attachCertificateValidator(plutusScript2);

        Result<String> delgResult = quickTxBuilder.compose(delegTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(delgResult);
        assertTrue(delgResult.isSuccessful());

        checkIfUtxoAvailable(delgResult.getValue(), sender1Addr);

        deregisterScriptsStakeKeys();
    }

    @Test
    @Order(11)
    void stakeDelegation_stakeKeys() {
        registerStakeKeys();
        registerScriptsStakeKeys();

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        //Delegation
        Tx delegTx = new Tx()
                .delegateTo(sender1Addr, poolId)
                .delegateTo(new Address(sender2Addr), poolId)
                .payToAddress(receiver1, Amount.ada(1.3))
                .payToAddress(receiver2, Amount.ada(2.2))
                .from(sender2Addr);

        ScriptTx scriptDelegationTx = new ScriptTx()
                .payToAddress(receiver2, Amount.ada(3.2))
                .delegateTo(scriptStakeAddress1, poolId, BigIntPlutusData.of(1))
                .delegateTo(new Address(scriptStakeAddress2), poolId, BigIntPlutusData.of(2))
                .attachMetadata(MessageMetadata.create().add("This is a delegation transaction"))
                .attachCertificateValidator(plutusScript1)
                .attachCertificateValidator(plutusScript2);

        Result<String> delgResult = quickTxBuilder.compose(delegTx, scriptDelegationTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1)) //for fee
                .withSigner(SignerProviders.signerFrom(sender2))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender2))
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(delgResult);
        assertTrue(delgResult.isSuccessful());

        checkIfUtxoAvailable(delgResult.getValue(), sender1Addr);

        deRegisterStakeKeys();
        deregisterScriptsStakeKeys();
    }


    @Test
    @Order(12)
    void stakeDelegation_nonScriptStakeKey_mixWithOtherPayments() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

       registerStakeKeys();
       registerScriptsStakeKeys();

        //Delegation
        Tx delegTx = new Tx()
                .delegateTo(new Address(sender1Addr), poolId)
                .delegateTo(new Address(sender2Addr), poolId)
                .attachMetadata(MessageMetadata.create().add("This is a delegation transaction"))
                .from(sender1Addr);

        Result<String> delgResult = quickTxBuilder.compose(delegTx)
                .withSigner(SignerProviders.signerFrom(sender1)) //for fee
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender2))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(delgResult);
        assertTrue(delgResult.isSuccessful());

        checkIfUtxoAvailable(delgResult.getValue(), sender1Addr);
    }

    @Test
    @Order(13)
    void withdrawal_regularAddress() throws ApiException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Address stakeAddress = AddressProvider.getStakeAddress(new Address(sender1Addr));
        //withdrawal
        Tx withdrawalTx = new Tx()
                .withdraw(stakeAddress, adaToLovelace(1.2))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(withdrawalTx)
                .build();

        //expected values
        BigInteger inputLovelace = getLovelaceInUtxo(transaction.getBody().getInputs().get(0).getTransactionId(),
                transaction.getBody().getInputs().get(0).getIndex());
        BigInteger totalInput = inputLovelace.add(transaction.getBody().getWithdrawals().get(0).getCoin());
        BigInteger outputCoin = transaction.getBody().getOutputs()
                        .get(0).getValue().getCoin();
        BigInteger totalOutput = transaction.getBody().getFee().add(outputCoin);

        assertThat(transaction.getBody().getWithdrawals()).hasSize(1);
        assertThat(transaction.getBody().getInputs()).hasSizeGreaterThan(0);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(sender1Addr);
        assertThat(totalInput).isEqualTo(totalOutput);
    }

    @Test
    @Order(14)
    void withdrawal_scriptAddress() throws ApiException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Address stakeAddress = AddressProvider.getStakeAddress(new Address(sender1Addr));

        //withdrawal
        ScriptTx withdrawalTx = new ScriptTx()
                .withdraw(stakeAddress, adaToLovelace(1.2), BigIntPlutusData.of(1))
                .withdraw(stakeAddress, adaToLovelace(2.3), BigIntPlutusData.of(2))
                .attachRewardValidator(plutusScript1)
                .attachRewardValidator(plutusScript2);

        Transaction transaction = quickTxBuilder.compose(withdrawalTx)
                .feePayer(sender1Addr)
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .build();

        //expected values
        BigInteger inputLovelace = getLovelaceInUtxo(transaction.getBody().getInputs().get(0).getTransactionId(),
                transaction.getBody().getInputs().get(0).getIndex());
        BigInteger totalInput = inputLovelace
                .add(transaction.getBody().getWithdrawals().get(0).getCoin())
                .add(transaction.getBody().getWithdrawals().get(1).getCoin());

        BigInteger outputCoin = transaction.getBody().getOutputs()
                .get(0).getValue().getCoin();
        BigInteger totalOutput = transaction.getBody().getFee().add(outputCoin);

        assertThat(transaction.getBody().getWithdrawals()).hasSize(2);
        assertThat(transaction.getWitnessSet().getPlutusV2Scripts()).hasSize(2);
        assertThat(transaction.getWitnessSet().getRedeemers()).hasSize(2);
        assertThat(transaction.getWitnessSet().getRedeemers().get(0).getIndex()).isEqualTo(0);
        assertThat(transaction.getWitnessSet().getRedeemers().get(1).getIndex()).isEqualTo(1);
        assertThat(transaction.getWitnessSet().getRedeemers().get(0).getTag()).isEqualTo(RedeemerTag.Reward);
        assertThat(transaction.getWitnessSet().getRedeemers().get(1).getTag()).isEqualTo(RedeemerTag.Reward);
        assertThat(transaction.getBody().getInputs()).hasSizeGreaterThan(0);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(sender1Addr);
        assertThat(totalInput).isEqualTo(totalOutput);
    }

    private BigInteger getLovelaceInUtxo(String txHash, int outputIndex) throws ApiException {
        Result<Utxo> utxoResult = backendService.getUtxoService().getTxOutput(txHash, outputIndex);
        return utxoResult.getValue().getAmount().stream().filter(amt -> amt.getUnit().equals("lovelace"))
                .findFirst().get().getQuantity();
    }

    private void registerScriptsStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        Tx tx = new Tx()
                .registerStakeAddress(scriptStakeAddress1)
                .registerStakeAddress(scriptStakeAddress2)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    private void deregisterScriptsStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(scriptStakeAddress1, BigIntPlutusData.of(1))
                .deregisterStakeAddress(scriptStakeAddress2, BigIntPlutusData.of(2))
                .attachCertificateValidator(plutusScript1)
                .attachCertificateValidator(plutusScript2);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    private void registerStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        Tx tx = new Tx()
                .registerStakeAddress(sender1Addr)
                .registerStakeAddress(sender2Addr)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    private void deRegisterStakeKeys() {
        Result<String> result = _deRegisterStakeKeys();
        assertTrue(result.isSuccessful());
    }

    private Result<String> _deRegisterStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        Tx tx = new Tx()
                .deregisterStakeAddress(sender1Addr)
                .deregisterStakeAddress(sender2Addr)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender2))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        return result;
    }
}
