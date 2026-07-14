package com.dreamy.domain.collection.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CollectionGroupRepositoryTest {

    @Mock
    CollectionGroupMapper groupMapper;
    @Mock
    CollectionGroupTranslationMapper translationMapper;
    @Mock
    CollectionMapper collectionMapper;

    @Test
    void deleteByIdRemovesTranslationsBeforeGroup() {
        CollectionGroupRepository repository = new CollectionGroupRepository(
                groupMapper, translationMapper, collectionMapper);

        repository.deleteById(12L);

        InOrder cleanup = inOrder(translationMapper, groupMapper);
        cleanup.verify(translationMapper).delete(any());
        cleanup.verify(groupMapper).deleteById(12L);
    }
}
