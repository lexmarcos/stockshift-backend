package br.com.stockshift.config;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StorageConfigTest {

    /**
     * Regression for PR #5: the thumbnail-row swap runs inside an afterCommit() callback for
     * inline products. A REQUIRED template would join the already-committed transaction and the
     * row swap would never commit, orphaning the uploaded R2 thumbnail objects. The template must
     * be REQUIRES_NEW so the swap always commits in its own transaction.
     */
    @Test
    void transactionTemplateUsesRequiresNewSoAfterCommitSwapsCommit() {
        StorageConfig config = new StorageConfig(mock(StorageProperties.class));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        TransactionTemplate template = config.transactionTemplate(transactionManager);

        assertThat(template.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
}
