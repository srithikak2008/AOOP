import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}

class Money {
    private final long amountInPaise;
    private final String currency;

    public Money(long amountInPaise, String currency) {
        if (amountInPaise < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Invalid currency");
        }
        this.amountInPaise = amountInPaise;
        this.currency = currency;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isPositive() {
        return amountInPaise > 0;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(Math.addExact(this.amountInPaise, other.amountInPaise), this.currency);
    }

    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        if (this.amountInPaise < other.amountInPaise) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        return new Money(Math.subtractExact(this.amountInPaise, other.amountInPaise), this.currency);
    }

    @Override
    public String toString() {
        return currency + " " + (amountInPaise / 100.0);
    }
}

class LedgerEntry {
    private final String transactionId;
    private final String transactionType;
    private final Money amount;
    private final String timestamp;

    public LedgerEntry(String transactionType, Money amount) {
        this.transactionId = UUID.randomUUID().toString();
        this.transactionType = transactionType;
        this.amount = amount;
        this.timestamp = Instant.now().toString();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Money getAmount() {
        return amount;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + transactionType + " | Amount: " + amount + " | ID: " + transactionId.substring(0, 8);
    }
}

class BankAccount {
    private final String accountNumber;
    private long balanceInPaise;
    private final Money overdraftLimit;
    private AccountStatus status;
    private final List<LedgerEntry> ledger;

    public BankAccount(String accountNumber, long initialBalanceInPaise, Money overdraftLimit) {
        if (accountNumber == null || accountNumber.isBlank() || initialBalanceInPaise < 0 || overdraftLimit == null) {
            throw new IllegalArgumentException("Invalid account details");
        }
        this.accountNumber = accountNumber;
        this.balanceInPaise = initialBalanceInPaise;
        this.overdraftLimit = overdraftLimit;
        this.status = AccountStatus.ACTIVE;
        this.ledger = new ArrayList<>();
    }

    public void deposit(Money money) {
        checkActive();
        if (money == null || !money.isPositive() || !money.getCurrency().equals(overdraftLimit.getCurrency())) {
            throw new IllegalArgumentException("Invalid deposit amount or currency");
        }
        balanceInPaise = Math.addExact(balanceInPaise, money.getAmountInPaise());
        ledger.add(new LedgerEntry("DEPOSIT", money));
    }

    public void withdraw(Money money) {
        checkActive();
        if (money == null || !money.isPositive() || !money.getCurrency().equals(overdraftLimit.getCurrency())) {
            throw new IllegalArgumentException("Invalid withdrawal amount or currency");
        }
        long amount = money.getAmountInPaise();
        if (amount > balanceInPaise + overdraftLimit.getAmountInPaise()) {
            throw new IllegalArgumentException("Overdraft limit exceeded");
        }
        balanceInPaise = Math.subtractExact(balanceInPaise, amount);
        ledger.add(new LedgerEntry("WITHDRAWAL", money));
    }

    public void freeze() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed account cannot be frozen");
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new IllegalStateException("Only frozen accounts can be unfrozen");
        }
        status = AccountStatus.ACTIVE;
    }

    public void close() {
        if (status != AccountStatus.ACTIVE || balanceInPaise < 0) {
            throw new IllegalStateException("Account cannot be closed while frozen or in overdraft");
        }
        status = AccountStatus.CLOSED;
    }

    private void checkActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Operation denied: Account is " + status);
        }
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public double getBalance() {
        return balanceInPaise / 100.0;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public List<LedgerEntry> getLedger() {
        return List.copyOf(ledger);
    }
}

class FundTransfer {
    private final String transferId;
    private final BankAccount source;
    private final BankAccount destination;
    private final Money amount;

    public FundTransfer(BankAccount source, BankAccount destination, Money amount) {
        this.transferId = UUID.randomUUID().toString();
        this.source = source;
        this.destination = destination;
        this.amount = amount;
    }

    public void execute() {
        if (source == null || destination == null || source.equals(destination)) {
            throw new IllegalArgumentException("Invalid source or destination account");
        }
        // Atomic-style transfer simulation with rollback on failure
        source.withdraw(amount);
        try {
            destination.deposit(amount);
            System.out.println("Transfer " + transferId.substring(0, 8) + " successful: " + amount);
        } catch (Exception e) {
            // Rollback source withdrawal if destination credit fails
            source.deposit(amount);
            throw new RuntimeException("Transfer failed and rolled back: " + e.getMessage());
        }
    }
}

public class BankingApp {
    public static void main(String[] args) {
        BankAccount accA = new BankAccount("ACC2001", 1500000, new Money(500000, "INR")); // Rs. 15,000 balance, Rs. 5,000 overdraft
        BankAccount accB = new BankAccount("ACC2002", 500000, new Money(200000, "INR"));  // Rs. 5,000 balance

        accA.deposit(new Money(250000, "INR"));   // Deposit Rs. 2,500 -> Rs. 17,500
        accA.withdraw(new Money(100000, "INR"));  // Withdraw Rs. 1,000 -> Rs. 16,500

        FundTransfer transfer = new FundTransfer(accA, accB, new Money(500000, "INR")); // Transfer Rs. 5,000
        transfer.execute();

        System.out.println("Account A (" + accA.getAccountNumber() + ") Balance: Rs. " + accA.getBalance() + " | Status: " + accA.getStatus());
        System.out.println("Account B (" + accB.getAccountNumber() + ") Balance: Rs. " + accB.getBalance() + " | Status: " + accB.getStatus());

        System.out.println("\n--- Account A Ledger ---");
        for (LedgerEntry entry : accA.getLedger()) {
            System.out.println(entry);
        }
    }
}
