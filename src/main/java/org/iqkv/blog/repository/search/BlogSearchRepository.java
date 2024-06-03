package org.iqkv.blog.repository.search;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import java.util.List;
import org.iqkv.blog.domain.Blog;
import org.iqkv.blog.repository.BlogRepository;
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
 * Spring Data Elasticsearch repository for the {@link Blog} entity.
 */
public interface BlogSearchRepository extends ElasticsearchRepository<Blog, Long>, BlogSearchRepositoryInternal {}

interface BlogSearchRepositoryInternal {
    Page<Blog> search(String query, Pageable pageable);

    Page<Blog> search(Query query);

    @Async
    void index(Blog entity);

    @Async
    void deleteFromIndexById(Long id);
}

class BlogSearchRepositoryInternalImpl implements BlogSearchRepositoryInternal {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final BlogRepository repository;

    BlogSearchRepositoryInternalImpl(ElasticsearchTemplate elasticsearchTemplate, BlogRepository repository) {
        this.elasticsearchTemplate = elasticsearchTemplate;
        this.repository = repository;
    }

    @Override
    public Page<Blog> search(String query, Pageable pageable) {
        NativeQuery nativeQuery = new NativeQuery(QueryStringQuery.of(qs -> qs.query(query))._toQuery());
        return search(nativeQuery.setPageable(pageable));
    }

    @Override
    public Page<Blog> search(Query query) {
        SearchHits<Blog> searchHits = elasticsearchTemplate.search(query, Blog.class);
        List<Blog> hits = searchHits.map(SearchHit::getContent).stream().toList();
        return new PageImpl<>(hits, query.getPageable(), searchHits.getTotalHits());
    }

    @Override
    public void index(Blog entity) {
        repository.findOneWithEagerRelationships(entity.getId()).ifPresent(elasticsearchTemplate::save);
    }

    @Override
    public void deleteFromIndexById(Long id) {
        elasticsearchTemplate.delete(String.valueOf(id), Blog.class);
    }
}
