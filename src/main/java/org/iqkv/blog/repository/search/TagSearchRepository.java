package org.iqkv.blog.repository.search;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import java.util.List;
import org.iqkv.blog.domain.Tag;
import org.iqkv.blog.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.scheduling.annotation.Async;

/**
 * Spring Data Elasticsearch repository for the {@link Tag} entity.
 */
public interface TagSearchRepository extends ElasticsearchRepository<Tag, Long>, TagSearchRepositoryInternal {}

interface TagSearchRepositoryInternal {
    Page<Tag> search(String query, Pageable pageable);

    Page<Tag> search(Query query);

    @Async
    void index(Tag entity);

    @Async
    void deleteFromIndexById(Long id);
}

class TagSearchRepositoryInternalImpl implements TagSearchRepositoryInternal {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final TagRepository repository;

    TagSearchRepositoryInternalImpl(ElasticsearchTemplate elasticsearchTemplate, TagRepository repository) {
        this.elasticsearchTemplate = elasticsearchTemplate;
        this.repository = repository;
    }

    @Override
    public Page<Tag> search(String query, Pageable pageable) {
        NativeQuery nativeQuery = new NativeQuery(QueryStringQuery.of(qs -> qs.query(query))._toQuery());
        return search(nativeQuery.setPageable(pageable));
    }

    @Override
    public Page<Tag> search(Query query) {
        SearchHits<Tag> searchHits = elasticsearchTemplate.search(query, Tag.class);
        List<Tag> hits = searchHits.map(SearchHit::getContent).stream().toList();
        return new PageImpl<>(hits, query.getPageable(), searchHits.getTotalHits());
    }

    @Override
    public void index(Tag entity) {
        repository.findById(entity.getId()).ifPresent(elasticsearchTemplate::save);
    }

    @Override
    public void deleteFromIndexById(Long id) {
        elasticsearchTemplate.delete(String.valueOf(id), Tag.class);
    }
}
