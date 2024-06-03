package org.iqkv.blog.service.impl;

import java.util.Optional;
import org.iqkv.blog.domain.Blog;
import org.iqkv.blog.repository.BlogRepository;
import org.iqkv.blog.repository.search.BlogSearchRepository;
import org.iqkv.blog.service.BlogService;
import org.iqkv.blog.service.dto.BlogDTO;
import org.iqkv.blog.service.mapper.BlogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link org.iqkv.blog.domain.Blog}.
 */
@Service
@Transactional
public class BlogServiceImpl implements BlogService {

    private final Logger log = LoggerFactory.getLogger(BlogServiceImpl.class);

    private final BlogRepository blogRepository;

    private final BlogMapper blogMapper;

    private final BlogSearchRepository blogSearchRepository;

    public BlogServiceImpl(BlogRepository blogRepository, BlogMapper blogMapper, BlogSearchRepository blogSearchRepository) {
        this.blogRepository = blogRepository;
        this.blogMapper = blogMapper;
        this.blogSearchRepository = blogSearchRepository;
    }

    @Override
    public BlogDTO save(BlogDTO blogDTO) {
        log.debug("Request to save Blog : {}", blogDTO);
        Blog blog = blogMapper.toEntity(blogDTO);
        blog = blogRepository.save(blog);
        blogSearchRepository.index(blog);
        return blogMapper.toDto(blog);
    }

    @Override
    public BlogDTO update(BlogDTO blogDTO) {
        log.debug("Request to update Blog : {}", blogDTO);
        Blog blog = blogMapper.toEntity(blogDTO);
        blog = blogRepository.save(blog);
        blogSearchRepository.index(blog);
        return blogMapper.toDto(blog);
    }

    @Override
    public Optional<BlogDTO> partialUpdate(BlogDTO blogDTO) {
        log.debug("Request to partially update Blog : {}", blogDTO);

        return blogRepository
            .findById(blogDTO.getId())
            .map(existingBlog -> {
                blogMapper.partialUpdate(existingBlog, blogDTO);

                return existingBlog;
            })
            .map(blogRepository::save)
            .map(savedBlog -> {
                blogSearchRepository.index(savedBlog);
                return savedBlog;
            })
            .map(blogMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlogDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Blogs");
        return blogRepository.findAll(pageable).map(blogMapper::toDto);
    }

    public Page<BlogDTO> findAllWithEagerRelationships(Pageable pageable) {
        return blogRepository.findAllWithEagerRelationships(pageable).map(blogMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BlogDTO> findOne(Long id) {
        log.debug("Request to get Blog : {}", id);
        return blogRepository.findOneWithEagerRelationships(id).map(blogMapper::toDto);
    }

    @Override
    public void delete(Long id) {
        log.debug("Request to delete Blog : {}", id);
        blogRepository.deleteById(id);
        blogSearchRepository.deleteFromIndexById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlogDTO> search(String query, Pageable pageable) {
        log.debug("Request to search for a page of Blogs for query {}", query);
        return blogSearchRepository.search(query, pageable).map(blogMapper::toDto);
    }
}
