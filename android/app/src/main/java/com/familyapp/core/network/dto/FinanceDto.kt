package com.familyapp.core.network.dto

import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.core.database.entity.SyncStatus
import com.google.gson.annotations.SerializedName

data class AccountDto(
    @SerializedName("id") val id: String,
    @SerializedName("family_id") val familyId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("initial_balance") val initialBalance: Double = 0.0,
    @SerializedName("color_hex") val colorHex: String = "#1E523A",
    @SerializedName("current_balance") val currentBalance: Double? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
) {
    fun toEntity(defaultFamilyId: String, syncStatus: SyncStatus = SyncStatus.SYNCED): AccountEntity {
        return AccountEntity(
            id = id,
            familyId = familyId ?: defaultFamilyId,
            name = name,
            initialBalance = initialBalance,
            colorHex = colorHex,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncStatus = syncStatus
        )
    }

    companion object {
        fun fromEntity(entity: AccountEntity): AccountDto {
            return AccountDto(
                id = entity.id,
                familyId = entity.familyId,
                name = entity.name,
                initialBalance = entity.initialBalance,
                colorHex = entity.colorHex,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
        }
    }
}

data class FinanceTransactionDto(
    @SerializedName("id") val id: String,
    @SerializedName("family_id") val familyId: String? = null,
    @SerializedName("account_id") val accountId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("category") val category: String,
    @SerializedName("description") val description: String,
    @SerializedName("date") val date: String,
    @SerializedName("type") val type: String = "EXPENSE",
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
) {
    fun toEntity(defaultFamilyId: String, syncStatus: SyncStatus = SyncStatus.SYNCED): FinanceTransactionEntity {
        return FinanceTransactionEntity(
            id = id,
            familyId = familyId ?: defaultFamilyId,
            accountId = accountId,
            amount = amount,
            category = category,
            description = description,
            date = date,
            type = type,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncStatus = syncStatus
        )
    }

    companion object {
        fun fromEntity(entity: FinanceTransactionEntity): FinanceTransactionDto {
            return FinanceTransactionDto(
                id = entity.id,
                familyId = entity.familyId,
                accountId = entity.accountId,
                amount = entity.amount,
                category = entity.category,
                description = entity.description,
                date = entity.date,
                type = entity.type,
                createdBy = entity.createdBy,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
        }
    }
}

data class BatchSyncFinanceRequestDto(
    @SerializedName("accounts") val accounts: List<AccountDto> = emptyList(),
    @SerializedName("transactions") val transactions: List<FinanceTransactionDto> = emptyList(),
    @SerializedName("since") val since: String? = null
)

data class BatchSyncFinanceResponseDto(
    @SerializedName("synced_account_ids") val syncedAccountIds: List<String>,
    @SerializedName("server_accounts") val serverAccounts: List<AccountDto>,
    @SerializedName("synced_transaction_ids") val syncedTransactionIds: List<String>,
    @SerializedName("server_transactions") val serverTransactions: List<FinanceTransactionDto>
)
