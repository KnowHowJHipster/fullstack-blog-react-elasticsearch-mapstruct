package org.iqkv.blog.service;

import java.util.Optional;
import org.iqkv.blog.service.dto.BlogDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service Interface for managing {@link org.iqkv.blog.domain.Blog}.
 */
public interface BlogService {
    /**
     * Save a blog.
     *
     * @param blogDTO the entity to save.
     * @return the persisted entity.
     */
    BlogDTO save(BlogDTO blogDTO);

    /**
     * Updates a blog.
     *
     * @param blogDTO the entity to update.
     * @return the persisted entity.
     */
    BlogDTO update(BlogDTO blogDTO);

    /**
     * Partially updates a blog.
     *
     * @param blogDTO the entity to update partially.
     * @return the persisted entity.
     */
    Optional<BlogDTO> partialUpdate(BlogDTO blogDTO);

    /**
     * Get all the blogs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Page<BlogDTO> findAll(Pageable pageable);

    /**
     * Get all the blogs with eager load of many-to-many relationships.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Page<BlogDTO> findAllWithEagerRelationships(Pageable pageable);

    /**
     * Get the "id" blog.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Optional<BlogDTO> findOne(Long id);

    /**
     * Delete the "id" blog.
     *
     * @param id the id of the entity.
     */
    void delete(Long id);

    /**
     * Search for the blog corresponding to the query.
     *
     * @param query the query of the search.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Page<BlogDTO> search(String query, Pageable pageable);
}
