package org.iqkv.blog.repository.search;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import java.util.List;
import org.iqkv.blog.domain.Post;
import org.iqkv.blog.repository.PostRepository;
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
 * Spring Data Elasticsearch repository for the {@link Post} entity.
 */
public interface PostSearchRepository extends ElasticsearchRepository<Post, Long>, PostSearchRepositoryInternal {}

interface PostSearchRepositoryInternal {
    Page<Post> search(String query, Pageable pageable);

    Page<Post> search(Query query);

    @Async
    void index(Post entity);

    @Async
    void deleteFromIndexById(Long id);
}

class PostSearchRepositoryInternalImpl implements PostSearchRepositoryInternal {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final PostRepository repository;

    PostSearchRepositoryInternalImpl(ElasticsearchTemplate elasticsearchTemplate, PostRepository repository) {
        this.elasticsearchTemplate = elasticsearchTemplate;
        this.repository = repository;
    }

    @Override
    public Page<Post> search(String query, Pageable pageable) {
        NativeQuery nativeQuery = new NativeQuery(QueryStringQuery.of(qs -> qs.query(query))._toQuery());
        return search(nativeQuery.setPageable(pageable));
    }

    @Override
    public Page<Post> search(Query query) {
        SearchHits<Post> searchHits = elasticsearchTemplate.search(query, Post.class);
        List<Post> hits = searchHits.map(SearchHit::getContent).stream().toList();
        return new PageImpl<>(hits, query.getPageable(), searchHits.getTotalHits());
    }

    @Override
    public void index(Post entity) {
        repository.findOneWithEagerRelationships(entity.getId()).ifPresent(elasticsearchTemplate::save);
    }

    @Override
    public void deleteFromIndexById(Long id) {
        elasticsearchTemplate.delete(String.valueOf(id), Post.class);
    }
}
